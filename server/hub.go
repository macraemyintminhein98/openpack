package main

// Hub features: things that belong to the place running this server, not to the signed pack.
//
//   notices.txt   Notice board. The hub owner edits this text file; visitors can only read it.
//
// The file lives in the hub folder (the folder that contains "pack", next to the START files).

import (
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"
	"strings"
)

const (
	noticesFile   = "notices.txt"
	maxNotices    = 30
	maxNoticeSize = 64 * 1024
)

type notice struct {
	Title string `json:"title"`
	Body  string `json:"body"`
}

// parseNotices reads the notice board file. Notices are separated by a line of three or more
// dashes. The first line of each is its title. Lines starting with # are ignored.
func parseNotices(text string) []notice {
	out := []notice{}
	text = strings.ReplaceAll(strings.TrimPrefix(text, "\uFEFF"), "\r\n", "\n")
	var cur []string
	flush := func() {
		for len(cur) > 0 && strings.TrimSpace(cur[0]) == "" {
			cur = cur[1:]
		}
		if len(cur) > 0 && len(out) < maxNotices {
			out = append(out, notice{
				Title: strings.TrimSpace(cur[0]),
				Body:  strings.TrimSpace(strings.Join(cur[1:], "\n")),
			})
		}
		cur = nil
	}
	for _, line := range strings.Split(text, "\n") {
		t := strings.TrimSpace(line)
		if strings.HasPrefix(t, "#") {
			continue
		}
		if len(t) >= 3 && strings.Trim(t, "-") == "" {
			flush()
			continue
		}
		cur = append(cur, line)
	}
	flush()
	return out
}

type hub struct {
	dir string
}

func newHub(dir string) *hub { return &hub{dir: dir} }

func (h *hub) notices() []notice {
	if h.dir == "" {
		return []notice{}
	}
	f, err := os.Open(filepath.Join(h.dir, noticesFile))
	if err != nil {
		return []notice{}
	}
	defer f.Close()
	buf := make([]byte, maxNoticeSize)
	n, _ := f.Read(buf)
	return parseNotices(string(buf[:n]))
}

func writeJSON(w http.ResponseWriter, code int, v interface{}) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}

func (h *hub) register(mux *http.ServeMux) {
	mux.HandleFunc("/hub/notices.json", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]interface{}{"notices": h.notices()})
	})
}
