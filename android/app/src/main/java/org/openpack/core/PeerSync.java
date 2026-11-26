package org.openpack.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Phone-to-phone updates. One phone shares; another finds it on the same Wi-Fi or hotspot and
 * copies the pack across. This is how a new pack travels from town to village with no internet.
 *
 * Rules:
 *  - Only addresses on a local network are ever contacted (10.x, 172.16-31.x, 192.168.x, 169.254.x).
 *  - The other phone is not trusted. Its manifest must carry a valid signature from a key built
 *    into this app before a single content file is asked for, and every file is hashed at the end.
 *  - Files this phone already has are not copied again, and a broken transfer carries on where it stopped.
 */
public final class PeerSync {
    private PeerSync() {}

    public interface Progress { void step(int done, int total, String path); }

    /** What another phone offers, already signature-checked. */
    public static final class Offer {
        public String base;
        public byte[] manifest, sig, js;
        public JSONObject json;
        public String name, title, version, created;
    }

    public static final class Result { public int reused, downloaded; public long bytesDownloaded; }

    // ---------------------------------------------------------------- addresses

    /** Accepts "192.168.43.1", "192.168.43.1:8080" or a full http address; returns "http://ip:port/". */
    public static String normalize(String input) throws IOException {
        if (input == null) throw new IOException("no address");
        String s = input.trim();
        if (s.startsWith("http://")) s = s.substring(7);
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        int port = 8080;
        int colon = s.indexOf(':');
        if (colon >= 0) {
            try { port = Integer.parseInt(s.substring(colon + 1)); } catch (NumberFormatException e) { throw new IOException("bad address"); }
            s = s.substring(0, colon);
        }
        if (port < 1 || port > 65535 || !isLocalAddress(s)) throw new IOException("not a nearby address: " + input.trim());
        return "http://" + s + ":" + port + "/";
    }

    /** Numbers only, and only ranges that cannot be on the internet. */
    public static boolean isLocalAddress(String ip) {
        String[] p = ip == null ? new String[0] : ip.split("\\.", -1);
        if (p.length != 4) return false;
        int[] n = new int[4];
        for (int i = 0; i < 4; i++) {
            if (p[i].isEmpty() || p[i].length() > 3) return false;
            for (int k = 0; k < p[i].length(); k++) if (p[i].charAt(k) < '0' || p[i].charAt(k) > '9') return false;
            n[i] = Integer.parseInt(p[i]);
            if (n[i] > 255) return false;
        }
        return n[0] == 10 || n[0] == 127 || (n[0] == 172 && n[1] >= 16 && n[1] <= 31)
            || (n[0] == 192 && n[1] == 168) || (n[0] == 169 && n[1] == 254);
    }

    /**
     * Where to look for a sharing phone, most likely first: the hotspot owner is nearly always
     * x.y.z.1, then the rest of this phone's own network.
     */
    public static List<String> candidates(List<String> ownIps) {
        List<String> first = new ArrayList<String>(), rest = new ArrayList<String>();
        for (String ip : ownIps) {
            if (!isLocalAddress(ip)) continue;
            String net = ip.substring(0, ip.lastIndexOf('.') + 1);
            for (int i = 1; i < 255; i++) {
                String c = net + i;
                if (ownIps.contains(c) || first.contains(c) || rest.contains(c)) continue;
                if (i == 1) first.add(c); else rest.add(c);
            }
        }
        first.addAll(rest);
        return first;
    }

    /** Looks for OpenPack servers among the candidates. Quick: many are tried at once, each for a fraction of a second. */
    public static List<String> find(List<String> ips, final int port, final int timeoutMs) {
        ExecutorService pool = Executors.newFixedThreadPool(48);
        List<Future<String>> jobs = new ArrayList<Future<String>>();
        for (final String ip : ips) {
            jobs.add(pool.submit(new Callable<String>() {
                @Override public String call() {
                    Socket s = new Socket();
                    try {
                        s.connect(new InetSocketAddress(ip, port), timeoutMs);
                    } catch (IOException e) {
                        return null;
                    } finally {
                        try { s.close(); } catch (IOException ignored) {}
                    }
                    try {
                        String base = "http://" + ip + ":" + port + "/";
                        String f = new String(get(base + "features.json", 1 << 16, 3000), StandardCharsets.UTF_8);
                        return f.contains("\"urls\"") && f.contains("\"version\"") ? base : null;
                    } catch (IOException e) {
                        return null;
                    }
                }
            }));
        }
        List<String> found = new ArrayList<String>();
        for (Future<String> j : jobs) {
            try {
                String b = j.get();
                if (b != null) found.add(b);
            } catch (Exception ignored) {}
        }
        pool.shutdownNow();
        return found;
    }

    // ---------------------------------------------------------------- transfer

    /** Asks the other phone what it has and checks the signature. Nothing is written. */
    public static Offer look(String base, List<byte[]> keys) throws IOException, PackVerifier.PackException {
        Offer o = new Offer();
        o.base = normalize(base);
        o.manifest = get(o.base + "manifest.json", 64 << 20, 15000);
        o.sig = get(o.base + "manifest.sig", 4096, 15000);
        o.js = get(o.base + "manifest.js", (64 << 20) + 64, 15000);
        PackVerifier.checkSignature(o.manifest, o.sig, keys);
        PackVerifier.checkManifestJs(o.js, o.manifest);
        try {
            o.json = new JSONObject(new String(o.manifest, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new PackVerifier.PackException("manifest.json is not valid JSON");
        }
        o.name = o.json.optString("name", "");
        o.title = o.json.optString("title", o.name);
        o.version = o.json.optString("version", "");
        o.created = o.json.optString("created", "");
        return o;
    }

    /**
     * Copies the offered pack into dest. Files the current pack already has (same path, same hash)
     * are taken from this phone. dest may hold an unfinished earlier attempt at the same pack.
     * Returns only after the finished copy passes the full check.
     */
    public static Result fetch(Offer o, File dest, PackVerifier.Pack current, List<byte[]> keys, Progress progress)
            throws IOException, PackVerifier.PackException {
        File mf = new File(dest, "manifest.json");
        if (dest.exists() && !(mf.isFile() && java.util.Arrays.equals(readAll(mf), o.manifest))) {
            PackZip.deleteTree(dest); // an unfinished copy of some other pack
        }
        if (!dest.isDirectory() && !dest.mkdirs()) throw new IOException("cannot create folder");
        write(mf, o.manifest);
        write(new File(dest, "manifest.sig"), o.sig);
        write(new File(dest, "manifest.js"), o.js);

        Map<String, String> have = new HashMap<String, String>();
        if (current != null) {
            JSONArray ci = current.manifest.optJSONArray("items");
            for (int i = 0; ci != null && i < ci.length(); i++) {
                JSONObject it = ci.optJSONObject(i);
                if (it != null) have.put(it.optString("path"), it.optString("sha256") + ":" + it.optLong("bytes", -1));
            }
        }
        JSONArray items = o.json.optJSONArray("items");
        if (items == null) throw new PackVerifier.PackException("manifest.json has no items");
        Result r = new Result();
        String root = dest.getCanonicalPath() + File.separator;
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            String path = it == null ? "" : it.optString("path", "");
            if (!PackVerifier.isSafeContentPath(path)) throw new PackVerifier.PackException("unsafe path in manifest: " + path);
            long want = it.optLong("bytes", -1);
            if (want < 0) throw new PackVerifier.PackException("manifest has no size for " + path);
            File out = new File(dest, path);
            if (!out.getCanonicalPath().startsWith(root)) throw new PackVerifier.PackException("unsafe path in manifest: " + path);
            if (progress != null) progress.step(i, items.length(), path);
            File parent = out.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("cannot create folder");
            if (out.isFile() && out.length() == want) continue; // finished in an earlier attempt; hashed below
            if (out.isFile() && out.length() > want) out.delete();
            String key = it.optString("sha256") + ":" + want;
            File mine = current == null ? null : new File(current.dir, path);
            if (mine != null && key.equals(have.get(path)) && mine.isFile() && mine.length() == want) {
                copy(mine, out);
                r.reused++;
                continue;
            }
            r.bytesDownloaded += download(o.base + encodePath(path), out, want);
            r.downloaded++;
        }
        if (progress != null) progress.step(items.length(), items.length(), "");
        try {
            PackVerifier.verify(dest, keys, true, null);
        } catch (PackVerifier.PackException e) {
            PackZip.deleteTree(dest); // a damaged copy must not be picked up again
            throw e;
        }
        return r;
    }

    /**
     * How many bytes a copy of this offer still needs on disk, counting what an earlier attempt
     * already left in dest. Cheap phones are often nearly full: say so before starting, not halfway.
     */
    public static long bytesStillNeeded(Offer o, File dest) {
        long need = 0;
        JSONArray items = o.json.optJSONArray("items");
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            long want = Math.max(0, it.optLong("bytes", 0));
            String path = it.optString("path", "");
            File have = PackVerifier.isSafeContentPath(path) ? new File(dest, path) : null;
            long got = have != null && have.isFile() ? Math.min(have.length(), want) : 0;
            need += want - got;
        }
        return need;
    }

    private static long download(String url, File out, long want) throws IOException {
        long have = out.isFile() ? out.length() : 0;
        HttpURLConnection c = open(url, 20000);
        if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
        try {
            int code = c.getResponseCode();
            boolean append = code == 206 && have > 0;
            if (code != 200 && code != 206) throw new IOException("the other phone answered " + code + " for " + out.getName());
            if (!append) have = 0;
            long got = 0;
            InputStream in = c.getInputStream();
            try {
                OutputStream os = new FileOutputStream(out, append);
                try {
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        got += n;
                        if (have + got > want) throw new IOException(out.getName() + " is larger than the manifest says");
                        os.write(buf, 0, n);
                    }
                } finally {
                    os.close();
                }
            } finally {
                in.close();
            }
            if (have + got != want) throw new IOException("the copy of " + out.getName() + " stopped early");
            return got;
        } finally {
            c.disconnect();
        }
    }

    static String encodePath(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("/")) {
            if (sb.length() > 0) sb.append('/');
            sb.append(URLEncoder.encode(part, "UTF-8").replace("+", "%20"));
        }
        return sb.toString();
    }

    private static HttpURLConnection open(String url, int timeoutMs) throws IOException {
        URL u = new URL(url);
        if (!"http".equals(u.getProtocol()) || !isLocalAddress(u.getHost())) throw new IOException("not a nearby address");
        HttpURLConnection c = (HttpURLConnection) u.openConnection(Proxy.NO_PROXY); // never through a proxy
        c.setConnectTimeout(Math.min(timeoutMs, 5000));
        c.setReadTimeout(timeoutMs);
        c.setInstanceFollowRedirects(false); // a redirect could point somewhere that is not nearby
        c.setUseCaches(false);
        return c;
    }

    static byte[] get(String url, int max, int timeoutMs) throws IOException {
        HttpURLConnection c = open(url, timeoutMs);
        try {
            if (c.getResponseCode() != 200) throw new IOException("the other phone answered " + c.getResponseCode());
            InputStream in = c.getInputStream();
            try {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (o.size() + n > max) throw new IOException("answer too large");
                    o.write(buf, 0, n);
                }
                return o.toByteArray();
            } finally {
                in.close();
            }
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            return o.toByteArray();
        } finally {
            in.close();
        }
    }

    private static void write(File f, byte[] b) throws IOException {
        OutputStream os = new FileOutputStream(f);
        try { os.write(b); } finally { os.close(); }
    }

    private static void copy(File from, File to) throws IOException {
        InputStream in = new FileInputStream(from);
        try {
            OutputStream os = new FileOutputStream(to);
            try {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            } finally {
                os.close();
            }
        } finally {
            in.close();
        }
    }

    /** Sorted, for showing a short list of what was found. */
    public static List<String> sorted(List<String> l) {
        List<String> c = new ArrayList<String>(l);
        Collections.sort(c);
        return c;
    }
}
