// openpack: a single-file offline content server.
//
// Run it on any cheap Windows laptop, Android phone (via Termux) or Linux box. Everyone on the
// same Wi-Fi or phone hotspot can then browse the pack in a normal browser. No internet, no
// accounts, no logs. Standard library only, so it cross-compiles to one small static file.
package main

import (
	"bufio"
	"encoding/json"
	"flag"
	"fmt"
	"mime"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

const version = "0.4.0"

func main() {
	dirFlag := flag.String("dir", "", "pack folder (default: ./pack next to this program, or the current folder)")
	port := flag.Int("port", 8080, "port to listen on (use 80 for a shorter address if allowed)")
	pubFlag := flag.String("pub", "", "public key file to verify the pack signature (default: openpack.pub next to this program)")
	fast := flag.Bool("fast", false, "skip hashing every file at start-up (faster for big packs, less safe)")
	check := flag.Bool("check", false, "only verify the pack and exit")
	force := flag.Bool("force", false, "serve even if verification fails (not recommended)")
	showVersion := flag.Bool("version", false, "print version and exit")
	flag.Parse()

	if *showVersion {
		fmt.Println("openpack", version)
		return
	}

	exe, _ := os.Executable()
	exeDir := filepath.Dir(exe)

	dir, err := findPack(*dirFlag, exeDir)
	if err != nil {
		fatal(err.Error())
	}
	pub := findPub(*pubFlag, exeDir, dir)

	fmt.Printf("OpenPack %s\n", version)
	fmt.Printf("Pack folder: %s\n", dir)
	if pub == "" {
		fmt.Println("WARNING: no public key found, so the signature is NOT checked (file hashes still are).")
	}

	start := time.Now()
	fmt.Println("Checking pack...")
	m, err := verifyPack(dir, pub, !*fast, func(s string) { fmt.Println(s) })
	if err != nil {
		fmt.Println("PACK CHECK FAILED:", err)
		if !*force {
			fatal("Not serving this pack. Copy it again from the original source, or use -force to override.")
		}
		fmt.Println("Continuing because -force was given.")
	} else {
		sig := "signature OK, "
		if pub == "" {
			sig = ""
		}
		fmt.Printf("OK: %s v%s, %s%d items verified in %.1fs\n", m.Name, m.Version, sig, len(m.Items), time.Since(start).Seconds())
	}
	if *check {
		if err != nil {
			os.Exit(2)
		}
		return
	}

	registerMimeTypes()

	kiwixCmd := startKiwix(dir, exeDir)
	hasKiwix := kiwixCmd != nil

	ln, chosen := listen(*port)
	if ln == nil {
		fatal(fmt.Sprintf("could not open a port near %d (is another copy already running?)", *port))
	}
	urls := lanURLs(chosen)

	mux := http.NewServeMux()
	mux.HandleFunc("/features.json", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.Header().Set("Cache-Control", "no-store")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"kiwix": hasKiwix, "urls": urls, "version": version})
	})
	if hasKiwix {
		proxy := httputil.NewSingleHostReverseProxy(&url.URL{Scheme: "http", Host: "127.0.0.1:8081"})
		mux.Handle("/kiwix/", proxy)
		mux.HandleFunc("/kiwix", func(w http.ResponseWriter, r *http.Request) {
			http.Redirect(w, r, "/kiwix/", http.StatusFound)
		})
	}
	mux.Handle("/", http.FileServer(safeFS{http.Dir(dir)}))

	srv := &http.Server{
		Handler:           withHeaders(mux),
		ReadHeaderTimeout: 10 * time.Second,
		IdleTimeout:       60 * time.Second,
		// No WriteTimeout: large files over slow Wi-Fi must be allowed to finish.
	}

	printBanner(chosen, urls, hasKiwix)

	go func() {
		c := make(chan os.Signal, 1)
		signal.Notify(c, os.Interrupt)
		<-c
		fmt.Println("\nStopping.")
		if kiwixCmd != nil && kiwixCmd.Process != nil {
			_ = kiwixCmd.Process.Kill()
		}
		os.Exit(0)
	}()

	if err := srv.Serve(ln); err != nil && err != http.ErrServerClosed {
		fatal(err.Error())
	}
}

// findPack picks the pack folder: -dir, then ./pack next to the program, then the program's own
// folder, then the current folder. A pack folder is one that contains manifest.json.
func findPack(flagDir, exeDir string) (string, error) {
	cands := []string{}
	if flagDir != "" {
		cands = append(cands, flagDir)
	} else {
		cwd, _ := os.Getwd()
		cands = append(cands,
			filepath.Join(exeDir, "pack"), filepath.Join(exeDir, "..", "pack"),
			exeDir, filepath.Join(cwd, "pack"), cwd)
	}
	for _, c := range cands {
		if _, err := os.Stat(filepath.Join(c, "manifest.json")); err == nil {
			abs, _ := filepath.Abs(c)
			return abs, nil
		}
	}
	if flagDir != "" {
		return "", fmt.Errorf("no manifest.json in %s", flagDir)
	}
	return "", fmt.Errorf("could not find a pack folder (one containing manifest.json). Use -dir <folder>")
}

func findPub(flagPub, exeDir, packDir string) string {
	cands := []string{flagPub}
	if flagPub == "" {
		cwd, _ := os.Getwd()
		cands = []string{
			filepath.Join(exeDir, "openpack.pub"), filepath.Join(exeDir, "..", "openpack.pub"),
			filepath.Join(packDir, "..", "openpack.pub"), filepath.Join(cwd, "openpack.pub"),
		}
	}
	for _, c := range cands {
		if c == "" {
			continue
		}
		if _, err := os.Stat(c); err == nil {
			return c
		}
	}
	return ""
}

func registerMimeTypes() {
	// Set explicitly: on some Windows machines the registry maps .js/.css wrongly, which would
	// silently break the portal.
	for ext, typ := range map[string]string{
		".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8",
		".css": "text/css; charset=utf-8", ".json": "application/json; charset=utf-8",
		".txt": "text/plain; charset=utf-8", ".md": "text/plain; charset=utf-8",
		".svg": "image/svg+xml", ".apk": "application/vnd.android.package-archive",
		".zim": "application/octet-stream", ".woff2": "font/woff2", ".woff": "font/woff",
	} {
		_ = mime.AddExtensionType(ext, typ)
	}
}

// safeFS hides dotfiles and never lists directories (a folder without index.html is a 404).
type safeFS struct{ root http.Dir }

func (s safeFS) Open(name string) (http.File, error) {
	for _, part := range strings.Split(name, "/") {
		if strings.HasPrefix(part, ".") && part != "" && part != "." {
			return nil, os.ErrNotExist
		}
	}
	f, err := s.root.Open(name)
	if err != nil {
		return nil, err
	}
	st, err := f.Stat()
	if err != nil {
		f.Close()
		return nil, err
	}
	if st.IsDir() {
		idx, err := s.root.Open(strings.TrimSuffix(name, "/") + "/index.html")
		if err != nil {
			f.Close()
			return nil, os.ErrNotExist
		}
		idx.Close()
	}
	return f, nil
}

func withHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Referrer-Policy", "no-referrer")
		p := strings.ToLower(r.URL.Path)
		switch {
		case strings.HasSuffix(p, ".apk"):
			w.Header().Set("Content-Disposition", "attachment")
		case p == "/" || strings.HasSuffix(p, ".html") || strings.HasSuffix(p, ".json") || strings.HasSuffix(p, ".js"):
			w.Header().Set("Cache-Control", "no-cache")
		}
		next.ServeHTTP(w, r) // note: requests are deliberately not logged
	})
}

// listen tries the wanted port, then the next nine.
func listen(want int) (net.Listener, int) {
	for p := want; p < want+10; p++ {
		ln, err := net.Listen("tcp", fmt.Sprintf(":%d", p))
		if err == nil {
			return ln, p
		}
	}
	return nil, 0
}

// lanURLs lists addresses other devices can use. On Android 11+ the interface list is blocked for
// normal apps, so we fall back to asking the OS which address it would use for outgoing traffic.
func lanURLs(port int) []string {
	var ips []string
	seen := map[string]bool{}
	add := func(ip net.IP) {
		v4 := ip.To4()
		if v4 == nil || v4.IsLoopback() || v4.IsLinkLocalUnicast() {
			return
		}
		s := v4.String()
		if !seen[s] {
			seen[s] = true
			ips = append(ips, s)
		}
	}
	if ifs, err := net.Interfaces(); err == nil {
		for _, i := range ifs {
			if i.Flags&net.FlagUp == 0 || i.Flags&net.FlagLoopback != 0 {
				continue
			}
			addrs, _ := i.Addrs()
			for _, a := range addrs {
				if ipn, ok := a.(*net.IPNet); ok {
					add(ipn.IP)
				}
			}
		}
	}
	if len(ips) == 0 {
		if c, err := net.Dial("udp", "192.0.2.1:9"); err == nil { // no packet is sent
			if ua, ok := c.LocalAddr().(*net.UDPAddr); ok {
				add(ua.IP)
			}
			c.Close()
		}
	}
	out := make([]string, 0, len(ips))
	for _, ip := range ips {
		if port == 80 {
			out = append(out, "http://"+ip+"/")
		} else {
			out = append(out, fmt.Sprintf("http://%s:%d/", ip, port))
		}
	}
	return out
}

func printBanner(port int, urls []string, kiwix bool) {
	fmt.Println()
	fmt.Println("==============================================================")
	fmt.Println(" OpenPack is running. Leave this window open.")
	fmt.Println(" Other phones/laptops on the same Wi-Fi (or this device's")
	fmt.Println(" hotspot) can open ANY of these addresses in a browser:")
	fmt.Println()
	if len(urls) == 0 {
		fmt.Println("   (could not detect the address automatically)")
		fmt.Printf("   Find this device's IP in its Wi-Fi/hotspot settings, then use http://<that IP>:%d/\n", port)
		fmt.Println("   Android hotspots are usually 192.168.43.1")
	}
	for _, u := range urls {
		fmt.Println("   ", u)
	}
	fmt.Printf("\n On this device itself: http://localhost:%d/\n", port)
	if kiwix {
		fmt.Println(" Kiwix (offline Wikipedia) is running.")
	}
	if runtime.GOOS == "windows" {
		fmt.Println("\n If Windows asks about the firewall, tick 'Private networks' and click Allow.")
	}
	fmt.Println(" Press Ctrl+C to stop.")
	fmt.Println("==============================================================")
}

// startKiwix runs kiwix-serve for any .zim files in the pack, if kiwix-serve is available.
// It is optional: without it, .zim files are offered as downloads for the Kiwix phone app.
func startKiwix(packDir, exeDir string) *exec.Cmd {
	var zims []string
	_ = filepath.Walk(filepath.Join(packDir, "content"), func(p string, info os.FileInfo, err error) error {
		if err == nil && !info.IsDir() && strings.HasSuffix(strings.ToLower(p), ".zim") {
			zims = append(zims, p)
		}
		return nil
	})
	if len(zims) == 0 {
		return nil
	}
	name := "kiwix-serve"
	if runtime.GOOS == "windows" {
		name += ".exe"
	}
	bin, err := exec.LookPath(name)
	if err != nil {
		for _, c := range []string{filepath.Join(exeDir, name), filepath.Join(exeDir, "kiwix", name)} {
			if _, e := os.Stat(c); e == nil {
				bin, err = c, nil
				break
			}
		}
	}
	if err != nil {
		return nil
	}
	args := append([]string{"--port", "8081", "--address", "127.0.0.1", "--urlRootLocation", "/kiwix"}, zims...)
	cmd := exec.Command(bin, args...)
	if err := cmd.Start(); err != nil {
		return nil
	}
	done := make(chan error, 1)
	go func() { done <- cmd.Wait() }()
	select {
	case <-done:
		return nil // exited straight away: treat as unavailable
	case <-time.After(1200 * time.Millisecond):
		return cmd
	}
}

// fatal prints a message and, on Windows, waits so a double-clicked window does not vanish.
func fatal(msg string) {
	fmt.Println("\nERROR:", msg)
	if runtime.GOOS == "windows" {
		fmt.Print("\nPress Enter to close this window...")
		_, _ = bufio.NewReader(os.Stdin).ReadString('\n')
	}
	os.Exit(1)
}
