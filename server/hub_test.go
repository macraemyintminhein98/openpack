package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestParseNotices(t *testing.T) {
	got := parseNotices("\uFEFF# how to use: comment lines are ignored\r\n\r\nClinic day\r\nThursday 9 am\r\nBring your card\r\n---\n\nရေပေးဝေမည်\nမနက်ဖြန်\n-----\n\n---\nTitle only\n")
	if len(got) != 3 {
		t.Fatalf("want 3 notices, got %d: %+v", len(got), got)
	}
	if got[0].Title != "Clinic day" || got[0].Body != "Thursday 9 am\nBring your card" {
		t.Errorf("first notice wrong: %+v", got[0])
	}
	if got[1].Title != "ရေပေးဝေမည်" || got[1].Body != "မနက်ဖြန်" {
		t.Errorf("Burmese notice wrong: %+v", got[1])
	}
	if got[2].Title != "Title only" || got[2].Body != "" {
		t.Errorf("third notice wrong: %+v", got[2])
	}
	if n := parseNotices("# only a comment\n\n"); len(n) != 0 {
		t.Errorf("comment-only file should give no notices, got %+v", n)
	}
	if n := parseNotices(strings.Repeat("x\n---\n", 100)); len(n) != maxNotices {
		t.Errorf("want cap of %d notices, got %d", maxNotices, len(n))
	}
}

func hubServer(t *testing.T) (*hub, *httptest.Server, string) {
	t.Helper()
	dir := t.TempDir()
	h := newHub(dir)
	mux := http.NewServeMux()
	h.register(mux)
	srv := httptest.NewServer(mux)
	t.Cleanup(srv.Close)
	return h, srv, dir
}

func TestNoticesEndpointReadsTheFileLive(t *testing.T) {
	_, srv, dir := hubServer(t)
	get := func() []notice {
		res, err := http.Get(srv.URL + "/hub/notices.json")
		if err != nil {
			t.Fatal(err)
		}
		defer res.Body.Close()
		var out struct{ Notices []notice }
		_ = json.NewDecoder(res.Body).Decode(&out)
		return out.Notices
	}
	if n := get(); len(n) != 0 {
		t.Fatalf("no file: want 0 notices, got %d", len(n))
	}
	_ = os.WriteFile(filepath.Join(dir, noticesFile), []byte("Hello\nworld"), 0o644)
	if n := get(); len(n) != 1 || n[0].Title != "Hello" {
		t.Fatalf("after writing the file: %+v", n)
	}
}
