package main

import (
	"crypto/ed25519"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

type manifest struct {
	Name    string `json:"name"`
	Version string `json:"version"`
	Items   []struct {
		ID     string `json:"id"`
		Kind   string `json:"kind"`
		Path   string `json:"path"`
		Sha256 string `json:"sha256"`
		Bytes  int64  `json:"bytes"`
	} `json:"items"`
}

// verifyPack checks a built pack: signature (if pubPath is set), manifest.js consistency,
// and optionally every file's SHA-256. It mirrors lib/build.js verifyPack in the Node CLI,
// so a pack built on a laptop verifies on the box with no other tooling.
func verifyPack(dir, pubPath string, hashFiles bool, progress func(string)) (*manifest, error) {
	mb, err := os.ReadFile(filepath.Join(dir, "manifest.json"))
	if err != nil {
		return nil, fmt.Errorf("manifest.json not found in %s", dir)
	}
	var m manifest
	if err := json.Unmarshal(mb, &m); err != nil {
		return nil, fmt.Errorf("manifest.json is not valid JSON: %v", err)
	}

	if pubPath != "" {
		if err := verifySignature(dir, pubPath, mb); err != nil {
			return nil, err
		}
	}

	if err := checkManifestJS(dir, mb); err != nil {
		return nil, err
	}

	if hashFiles {
		for _, it := range m.Items {
			rel := filepath.FromSlash(it.Path)
			if !filepath.IsLocal(rel) {
				return nil, fmt.Errorf("unsafe path in manifest: %s", it.Path)
			}
			if progress != nil && it.Bytes > 50<<20 {
				progress(fmt.Sprintf("  checking %s (%d MB)...", it.Path, it.Bytes>>20))
			}
			f, err := os.Open(filepath.Join(dir, rel))
			if err != nil {
				return nil, fmt.Errorf("missing file: %s", it.Path)
			}
			h := sha256.New()
			_, err = io.Copy(h, f)
			f.Close()
			if err != nil {
				return nil, fmt.Errorf("cannot read %s: %v", it.Path, err)
			}
			if hex.EncodeToString(h.Sum(nil)) != strings.ToLower(it.Sha256) {
				return nil, fmt.Errorf("HASH MISMATCH: %s (file was changed or damaged)", it.Path)
			}
		}
	}
	return &m, nil
}

func verifySignature(dir, pubPath string, manifestBytes []byte) error {
	sigB, err := os.ReadFile(filepath.Join(dir, "manifest.sig"))
	if err != nil {
		return errors.New("a public key was given but the pack is unsigned (manifest.sig missing)")
	}
	sig, err := base64.StdEncoding.DecodeString(strings.TrimSpace(string(sigB)))
	if err != nil {
		return errors.New("manifest.sig is not valid base64")
	}
	pemBytes, err := os.ReadFile(pubPath)
	if err != nil {
		return fmt.Errorf("cannot read public key %s", pubPath)
	}
	block, _ := pem.Decode(pemBytes)
	if block == nil {
		return errors.New("public key file is not valid PEM")
	}
	k, err := x509.ParsePKIXPublicKey(block.Bytes)
	if err != nil {
		return fmt.Errorf("public key not understood: %v", err)
	}
	pub, ok := k.(ed25519.PublicKey)
	if !ok {
		return errors.New("public key is not an ed25519 key")
	}
	if !ed25519.Verify(pub, manifestBytes, sig) {
		return errors.New("SIGNATURE INVALID: the manifest was changed, or it was signed by a different key")
	}
	return nil
}

// checkManifestJS makes sure the no-server copy (manifest.js) says the same as the signed
// manifest.json, so it cannot be used to slip in different titles, links or hashes.
func checkManifestJS(dir string, manifestBytes []byte) error {
	b, err := os.ReadFile(filepath.Join(dir, "manifest.js"))
	if err != nil {
		return nil // optional
	}
	// Must be an exact copy of the signed manifest inside the assignment, so no script can ride along.
	want := "window.OPENPACK_MANIFEST = " + string(manifestBytes) + ";"
	if strings.TrimRight(string(b), " \t\r\n") != want {
		return errors.New("manifest.js does not match manifest.json")
	}
	return nil
}
