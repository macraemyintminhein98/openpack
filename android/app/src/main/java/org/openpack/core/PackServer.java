package org.openpack.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A very small read-only web server for one pack. It does what the Go server does
 * (server/main.go) and nothing more: GET and HEAD, byte ranges for video, no directory
 * listings, no hidden files, and it keeps no record of who asked for what.
 *
 * The page files (index.html, app.js ...) come from portalDir, which is the app's own copy.
 * manifest.* and content/ come from packDir, and a content file is only sent if the signed
 * manifest lists it.
 */
public final class PackServer {
    private final File portalDir, packDir, noticesFile;
    private final Set<String> listed;
    private final String version;
    private volatile ServerSocket socket;
    private volatile boolean lan;
    private volatile int port;
    private ExecutorService pool;
    private volatile File apk;          // the app's own installer, so it can pass itself on
    private volatile String zipName = "pack";
    private Thread acceptor;

    public PackServer(File portalDir, File packDir, Set<String> listedContent, File noticesFile, String version) {
        this.portalDir = portalDir;
        this.packDir = packDir;
        this.listed = listedContent;
        this.noticesFile = noticesFile;
        this.version = version;
    }

    /**
     * @param shareOnNetwork false: only this phone can connect. true: phones on the same Wi-Fi or hotspot can too.
     * @param wantPort first port to try; the next nine are tried if it is taken. 0 picks any free port.
     */
    public synchronized int start(boolean shareOnNetwork, int wantPort) throws IOException {
        stop();
        IOException last = null;
        // The same port matters: the page's saved settings belong to its address. A port that was
        // only just closed can take a moment to come free, so try it a few times before moving on.
        for (int attempt = 0; attempt < 20 && wantPort != 0 && socket == null; attempt++) {
            ServerSocket s = new ServerSocket();
            try {
                s.setReuseAddress(true);
                s.bind(new InetSocketAddress(shareOnNetwork ? null : InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), wantPort), 64);
                socket = s;
            } catch (IOException e) {
                last = e;
                try { s.close(); } catch (IOException ignored) {}
                try { Thread.sleep(50); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        for (int p = wantPort; socket == null && p < wantPort + 10; p++) {
            ServerSocket s = new ServerSocket();
            try {
                s.setReuseAddress(true);
                InetAddress bind = shareOnNetwork ? null : InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
                s.bind(new InetSocketAddress(bind, p), 64);
                socket = s;
                break;
            } catch (IOException e) {
                last = e;
                try { s.close(); } catch (IOException ignored) {}
                if (wantPort == 0) break;
            }
        }
        if (socket == null) throw last != null ? last : new IOException("no free port");
        lan = shareOnNetwork;
        port = socket.getLocalPort();
        pool = Executors.newCachedThreadPool();
        final ServerSocket s = socket;
        final ExecutorService ex = pool;
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                while (!s.isClosed()) {
                    try {
                        final Socket c = s.accept();
                        ex.execute(new Runnable() {
                            @Override public void run() { handle(c); }
                        });
                    } catch (IOException e) {
                        if (s.isClosed()) return;
                    } catch (RuntimeException e) {
                        return; // pool was shut down
                    }
                }
            }
        }, "openpack-server");
        t.setDaemon(true);
        t.start();
        acceptor = t;
        return port;
    }

    public synchronized void stop() {
        ServerSocket s = socket;
        socket = null;
        if (s != null) try { s.close(); } catch (IOException ignored) {}
        if (pool != null) { pool.shutdownNow(); pool = null; }
        Thread t = acceptor;
        acceptor = null;
        if (t != null && t != Thread.currentThread()) {
            try { t.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    /** Lets visitors download the app itself from /openpack.apk. No app store or internet needed to spread it. */
    public void setApk(File apkFile) { this.apk = apkFile != null && apkFile.isFile() ? apkFile : null; }

    /** Folder name used inside /pack.zip, for example example-2026.48 */
    public void setZipName(String name) { if (name != null) this.zipName = name.replaceAll("[^A-Za-z0-9._-]", "_"); }

    public boolean isRunning() { return socket != null; }
    public boolean isShared() { return lan; }
    public int port() { return port; }

    // ---------------------------------------------------------------- requests

    private void handle(Socket c) {
        try {
            c.setSoTimeout(15000);
            InputStream in = c.getInputStream();
            OutputStream out = c.getOutputStream();
            String head = readHead(in);
            if (head == null) return;
            String[] lines = head.split("\r\n");
            String[] rl = lines[0].split(" ");
            if (rl.length < 2) { simple(out, 400, "Bad Request", false); return; }
            String method = rl[0];
            boolean headOnly = method.equals("HEAD");
            if (!method.equals("GET") && !headOnly) {
                simple(out, 405, "Method Not Allowed", false);
                return;
            }
            String range = null;
            for (int i = 1; i < lines.length; i++) {
                int k = lines[i].indexOf(':');
                if (k > 0 && lines[i].substring(0, k).trim().equalsIgnoreCase("range")) {
                    range = lines[i].substring(k + 1).trim();
                }
            }
            String target = rl[1];
            int q = target.indexOf('?');
            if (q >= 0) target = target.substring(0, q);
            int h = target.indexOf('#');
            if (h >= 0) target = target.substring(0, h);
            String path;
            try {
                path = URLDecoder.decode(target.replace("+", "%2B"), "UTF-8");
            } catch (Exception e) {
                simple(out, 400, "Bad Request", headOnly);
                return;
            }
            route(out, path, range, headOnly);
        } catch (IOException ignored) {
            // the other phone went away; nothing to do and nothing is recorded
        } finally {
            try { c.close(); } catch (IOException ignored) {}
        }
    }

    private static String readHead(InputStream in) throws IOException {
        byte[] buf = new byte[16 * 1024];
        int n = 0;
        while (n < buf.length) {
            int b = in.read();
            if (b < 0) return null;
            buf[n++] = (byte) b;
            if (n >= 4 && buf[n - 4] == '\r' && buf[n - 3] == '\n' && buf[n - 2] == '\r' && buf[n - 1] == '\n') {
                return new String(buf, 0, n - 4, StandardCharsets.ISO_8859_1);
            }
        }
        return null;
    }

    private void route(OutputStream out, String path, String range, boolean headOnly) throws IOException {
        if (path.equals("/features.json")) {
            json(out, featuresJson(), headOnly);
            return;
        }
        if (path.equals("/hub/notices.json")) {
            json(out, Notices.toJson(Notices.read(noticesFile)), headOnly);
            return;
        }
        if (path.equals("/pack.zip")) {
            sendPackZip(out, headOnly);
            return;
        }
        File f = path.equals("/openpack.apk") ? apk : resolve(path);
        if (f == null) {
            simple(out, 404, "Not Found", headOnly);
            return;
        }
        sendFile(out, f, path, range, headOnly);
    }

    /** Maps a request path to a file, or null. This is the only place files are chosen. */
    public File resolve(String path) {
        if (path == null || !path.startsWith("/") || path.indexOf('\0') >= 0 || path.indexOf('\\') >= 0) return null;
        if (path.equals("/")) path = "/index.html";
        String rel = path.substring(1);
        for (String part : rel.split("/", -1)) {
            if (part.isEmpty() || part.startsWith(".")) return null; // also stops ".." and hidden files
        }
        File root;
        if (rel.equals("manifest.json") || rel.equals("manifest.js") || rel.equals("manifest.sig")) {
            root = packDir;
        } else if (rel.startsWith("content/")) {
            if (listed != null && !listed.contains(rel)) return null;
            root = packDir;
        } else {
            root = portalDir;
        }
        File f = new File(root, rel);
        try {
            String canon = f.getCanonicalPath();
            if (!canon.startsWith(root.getCanonicalPath() + File.separator)) return null;
        } catch (IOException e) {
            return null;
        }
        return f.isFile() ? f : null;
    }

    /**
     * The whole pack as one .zip, made on the fly: the three manifest files and every listed file.
     * A phone without the app can carry this file away; a phone with the app can open it.
     */
    private void sendPackZip(OutputStream out, boolean headOnly) throws IOException {
        StringBuilder sb = start(200, "OK");
        sb.append("Content-Type: application/zip\r\n");
        sb.append("Content-Disposition: attachment; filename=\"").append(zipName).append(".zip\"\r\n");
        sb.append("Cache-Control: no-store\r\n\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (headOnly) { out.flush(); return; }
        java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out);
        zip.setLevel(1); // most content (video, PDF, audio) is already compressed
        byte[] buf = new byte[1 << 16];
        List<String> names = new ArrayList<String>();
        names.add("manifest.json");
        names.add("manifest.js");
        names.add("manifest.sig");
        if (listed != null) {
            List<String> sorted = new ArrayList<String>(listed);
            Collections.sort(sorted);
            names.addAll(sorted);
        }
        for (String rel : names) {
            File f = new File(packDir, rel);
            if (!f.isFile()) continue;
            zip.putNextEntry(new java.util.zip.ZipEntry(zipName + "/" + rel));
            InputStream in = new FileInputStream(f);
            try {
                int n;
                while ((n = in.read(buf)) > 0) zip.write(buf, 0, n);
            } finally {
                in.close();
            }
            zip.closeEntry();
        }
        zip.finish();
        out.flush();
    }

    private String featuresJson() {
        try {
            JSONArray urls = new JSONArray();
            if (lan) for (String u : lanUrls(port)) urls.put(u);
            return new JSONObject()
                .put("kiwix", false)
                .put("urls", urls)
                .put("version", version)
                .put("notices", Notices.read(noticesFile).size())
                .put("app", "android")
                .put("apk", apk != null)
                .put("pack_zip", true)
                .toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private void sendFile(OutputStream out, File f, String path, String range, boolean headOnly) throws IOException {
        long len = f.length(), from = 0, to = len - 1;
        boolean partial = false;
        if (range != null && range.startsWith("bytes=") && range.indexOf(',') < 0) {
            String spec = range.substring(6).trim();
            int dash = spec.indexOf('-');
            try {
                if (dash == 0) {
                    long n = Long.parseLong(spec.substring(1));
                    if (n <= 0) throw new NumberFormatException();
                    from = Math.max(0, len - n);
                } else if (dash > 0) {
                    from = Long.parseLong(spec.substring(0, dash));
                    if (dash < spec.length() - 1) to = Math.min(to, Long.parseLong(spec.substring(dash + 1)));
                } else {
                    throw new NumberFormatException();
                }
                if (from < 0 || from > to || from >= len) throw new NumberFormatException();
                partial = true;
            } catch (NumberFormatException e) {
                StringBuilder sb = start(416, "Range Not Satisfiable");
                sb.append("Content-Range: bytes */").append(len).append("\r\nContent-Length: 0\r\n\r\n");
                out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
                out.flush();
                return;
            }
        }
        long count = to - from + 1;
        if (len == 0) count = 0;
        StringBuilder sb = start(partial ? 206 : 200, partial ? "Partial Content" : "OK");
        sb.append("Content-Type: ").append(mime(path)).append("\r\n");
        sb.append("Content-Length: ").append(count).append("\r\n");
        sb.append("Accept-Ranges: bytes\r\n");
        if (partial) sb.append("Content-Range: bytes ").append(from).append('-').append(to).append('/').append(len).append("\r\n");
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.equals("/openpack.apk")) {
            sb.append("Content-Disposition: attachment; filename=\"OpenPack.apk\"\r\n");
        } else if (lower.endsWith(".apk")) {
            sb.append("Content-Disposition: attachment\r\n");
        } else if (lower.equals("/") || lower.endsWith(".html") || lower.endsWith(".json") || lower.endsWith(".js")) {
            sb.append("Cache-Control: no-cache\r\n");
        }
        sb.append("\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (!headOnly && count > 0) {
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            try {
                raf.seek(from);
                byte[] buf = new byte[1 << 16];
                long left = count;
                while (left > 0) {
                    int n = raf.read(buf, 0, (int) Math.min(buf.length, left));
                    if (n <= 0) break;
                    out.write(buf, 0, n);
                    left -= n;
                }
            } finally {
                raf.close();
            }
        }
        out.flush();
    }

    private static StringBuilder start(int code, String text) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("HTTP/1.1 ").append(code).append(' ').append(text).append("\r\n");
        sb.append("Connection: close\r\n");
        sb.append("X-Content-Type-Options: nosniff\r\n");
        sb.append("Referrer-Policy: no-referrer\r\n");
        return sb;
    }

    private static void simple(OutputStream out, int code, String text, boolean headOnly) throws IOException {
        byte[] body = (code + " " + text + "\n").getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = start(code, text);
        sb.append("Content-Type: text/plain; charset=utf-8\r\nContent-Length: ").append(body.length).append("\r\n\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (!headOnly) out.write(body);
        out.flush();
    }

    private static void json(OutputStream out, String s, boolean headOnly) throws IOException {
        byte[] body = (s + "\n").getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = start(200, "OK");
        sb.append("Content-Type: application/json; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: ")
          .append(body.length).append("\r\n\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (!headOnly) out.write(body);
        out.flush();
    }

    // ---------------------------------------------------------------- types

    private static final Map<String, String> MIME = new HashMap<String, String>();
    static {
        String[] m = {
            "html", "text/html; charset=utf-8", "htm", "text/html; charset=utf-8",
            "js", "text/javascript; charset=utf-8", "css", "text/css; charset=utf-8",
            "json", "application/json; charset=utf-8", "txt", "text/plain; charset=utf-8",
            "md", "text/plain; charset=utf-8", "sig", "text/plain; charset=utf-8",
            "svg", "image/svg+xml", "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "gif", "image/gif", "webp", "image/webp", "ico", "image/x-icon",
            "woff", "font/woff", "woff2", "font/woff2", "ttf", "font/ttf",
            "pdf", "application/pdf", "epub", "application/epub+zip",
            "mp3", "audio/mpeg", "m4a", "audio/mp4", "ogg", "audio/ogg", "opus", "audio/ogg", "wav", "audio/wav",
            "mp4", "video/mp4", "m4v", "video/mp4", "webm", "video/webm", "3gp", "video/3gpp",
            "apk", "application/vnd.android.package-archive", "zip", "application/zip",
            "zim", "application/octet-stream"
        };
        for (int i = 0; i < m.length; i += 2) MIME.put(m[i], m[i + 1]);
    }

    public static String mime(String path) {
        if (path.equals("/")) return MIME.get("html");
        int dot = path.lastIndexOf('.');
        String t = dot < 0 ? null : MIME.get(path.substring(dot + 1).toLowerCase(Locale.ROOT));
        return t == null ? "application/octet-stream" : t;
    }

    // ---------------------------------------------------------------- addresses

    /** Addresses another phone on the same Wi-Fi or hotspot can type in. Mobile-data and VPN links are left out. */
    public static List<String> lanUrls(int port) {
        List<String> out = new ArrayList<String>();
        for (String ip : lanIps()) out.add("http://" + ip + (port == 80 ? "" : ":" + port) + "/");
        return out;
    }

    /** This device's own addresses on Wi-Fi, hotspot, USB or cable links. */
    public static List<String> lanIps() {
        List<String> out = new ArrayList<String>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || !isLocalLink(ni.getName())) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress() || a.isLinkLocalAddress()) continue;
                    if (!out.contains(a.getHostAddress())) out.add(a.getHostAddress());
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    static boolean isLocalLink(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        String[] skip = {"rmnet", "ccmni", "pdp", "v4-", "clat", "tun", "ppp", "dummy", "ip6", "sit", "p2p-dev", "ifb", "epdg", "umts", "seth"};
        for (String s : skip) if (n.startsWith(s)) return false;
        return true;
    }
}
