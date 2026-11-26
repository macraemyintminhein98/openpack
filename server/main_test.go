package main

import (
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// buildTestPack writes a tiny signed pack in the same format the Node CLI produces.
func buildTestPack(t *testing.T) (dir, pubPath string) {
	t.Helper()
	dir = t.TempDir()
	content := []byte("hello offline world")
	if err := os.MkdirAll(filepath.Join(dir, "content", "about"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "content", "about", "hi.txt"), content, 0o644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(content)
	m := map[string]interface{}{
		"format": 1, "name": "t", "version": "1",
		"items": []map[string]interface{}{{
			"id": "hi", "kind": "file", "path": "content/about/hi.txt",
			"sha256": hex.EncodeToString(sum[:]), "bytes": len(content),
		}},
	}
	mb, _ := json.MarshalIndent(m, "", "  ")
	os.WriteFile(filepath.Join(dir, "manifest.json"), mb, 0o644)
	os.WriteFile(filepath.Join(dir, "manifest.js"), []byte("window.OPENPACK_MANIFEST = "+string(mb)+";\n"), 0o644)
	os.WriteFile(filepath.Join(dir, "index.html"), []byte("<html>portal</html>"), 0o644)
	os.WriteFile(filepath.Join(dir, ".secret"), []byte("nope"), 0o644)

	pub, priv, _ := ed25519.GenerateKey(rand.Reader)
	sig := ed25519.Sign(priv, mb)
	os.WriteFile(filepath.Join(dir, "manifest.sig"), []byte(base64.StdEncoding.EncodeToString(sig)), 0o644)
	der, _ := x509.MarshalPKIXPublicKey(pub)
	pubPath = filepath.Join(dir, "..", filepath.Base(dir)+".pub")
	os.WriteFile(pubPath, pem.EncodeToMemory(&pem.Block{Type: "PUBLIC KEY", Bytes: der}), 0o644)
	return dir, pubPath
}

func TestVerifyGoodPack(t *testing.T) {
	dir, pub := buildTestPack(t)
	if _, err := verifyPack(dir, pub, true, nil); err != nil {
		t.Fatalf("good pack rejected: %v", err)
	}
}

func TestVerifyDetectsFileTamper(t *testing.T) {
	dir, pub := buildTestPack(t)
	f, _ := os.OpenFile(filepath.Join(dir, "content", "about", "hi.txt"), os.O_APPEND|os.O_WRONLY, 0o644)
	f.WriteString("evil")
	f.Close()
	_, err := verifyPack(dir, pub, true, nil)
	if err == nil || !strings.Contains(err.Error(), "HASH MISMATCH") {
		t.Fatalf("expected hash mismatch, got %v", err)
	}
}

func TestVerifyDetectsManifestTamper(t *testing.T) {
	dir, pub := buildTestPack(t)
	p := filepath.Join(dir, "manifest.json")
	b, _ := os.ReadFile(p)
	os.WriteFile(p, []byte(strings.Replace(string(b), `"version": "1"`, `"version": "2"`, 1)), 0o644)
	_, err := verifyPack(dir, pub, true, nil)
	if err == nil || !strings.Contains(err.Error(), "SIGNATURE INVALID") {
		t.Fatalf("expected signature failure, got %v", err)
	}
}

func TestVerifyDetectsManifestJSTamper(t *testing.T) {
	dir, pub := buildTestPack(t)
	p := filepath.Join(dir, "manifest.js")
	b, _ := os.ReadFile(p)
	os.WriteFile(p, []byte(strings.Replace(string(b), `"version": "1"`, `"version": "9"`, 1)), 0o644)
	_, err := verifyPack(dir, pub, true, nil)
	if err == nil || !strings.Contains(err.Error(), "manifest.js") {
		t.Fatalf("expected manifest.js mismatch, got %v", err)
	}
}

func TestWrongKeyRejected(t *testing.T) {
	dir, _ := buildTestPack(t)
	_, other := buildTestPack(t)
	if _, err := verifyPack(dir, other, true, nil); err == nil {
		t.Fatal("pack verified with the wrong key")
	}
}

func serve(t *testing.T, dir string) *httptest.Server {
	registerMimeTypes()
	return httptest.NewServer(withHeaders(http.FileServer(safeFS{http.Dir(dir)})))
}

func TestServerHidesDotfilesAndDirectoryListings(t *testing.T) {
	dir, _ := buildTestPack(t)
	s := serve(t, dir)
	defer s.Close()
	for path, want := range map[string]int{
		"/":                         200, // index.html
		"/content/about/hi.txt":     200,
		"/.secret":                  404,
		"/content/":                 404, // no directory listing
		"/content/about/":           404,
		"/../../etc/passwd":         404,
		"/%2e%2e/%2e%2e/etc/passwd": 404,
	} {
		resp, err := http.Get(s.URL + path)
		if err != nil {
			t.Fatal(err)
		}
		resp.Body.Close()
		if resp.StatusCode != want {
			t.Errorf("%s: got %d, want %d", path, resp.StatusCode, want)
		}
	}
}

func TestRangeRequestsAndMime(t *testing.T) {
	dir, _ := buildTestPack(t)
	s := serve(t, dir)
	defer s.Close()
	req, _ := http.NewRequest("GET", s.URL+"/content/about/hi.txt", nil)
	req.Header.Set("Range", "bytes=0-4")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	body, _ := io.ReadAll(resp.Body)
	resp.Body.Close()
	if resp.StatusCode != 206 || string(body) != "hello" {
		t.Fatalf("range request: %d %q", resp.StatusCode, body)
	}
	resp, _ = http.Get(s.URL + "/manifest.js")
	resp.Body.Close()
	if ct := resp.Header.Get("Content-Type"); !strings.HasPrefix(ct, "text/javascript") {
		t.Errorf("manifest.js served as %q", ct)
	}
}

func TestFindPack(t *testing.T) {
	dir, _ := buildTestPack(t)
	got, err := findPack(dir, "/nonexistent")
	if err != nil || got == "" {
		t.Fatalf("findPack(-dir): %v", err)
	}
	if _, err := findPack(t.TempDir(), "/nonexistent"); err == nil {
		t.Fatal("expected error for folder without manifest.json")
	}
}

func TestManifestJSCannotCarryScript(t *testing.T) {
	dir, pub := buildTestPack(t)
	p := filepath.Join(dir, "manifest.js")
	good, _ := os.ReadFile(p)
	for _, bad := range []string{
		"fetch('http://x/');" + string(good),
		strings.TrimSpace(string(good)) + "fetch('http://x/')",
	} {
		os.WriteFile(p, []byte(bad), 0o644)
		if _, err := verifyPack(dir, pub, true, nil); err == nil || !strings.Contains(err.Error(), "manifest.js") {
			t.Fatalf("script in manifest.js was accepted: %v", err)
		}
	}
}
