package org.openpack.core;

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks a built pack the same way the Go server does (server/verify.go):
 * the ed25519 signature over manifest.json, that manifest.js says the same as manifest.json,
 * and the size and SHA-256 of every listed file.
 *
 * The app is stricter than the Go server in two ways: a signature is always required,
 * and every listed file must live under content/.
 */
public final class PackVerifier {

    /** A pack that passed every check. */
    public static final class Pack {
        public final File dir;
        public final JSONObject manifest;
        /** Every file path the manifest lists, for example content/health/first-aid.pdf */
        public final Set<String> paths;
        public final String name, title, titleMy, version;

        Pack(File dir, JSONObject m, Set<String> paths) {
            this.dir = dir;
            this.manifest = m;
            this.paths = Collections.unmodifiableSet(paths);
            this.name = m.optString("name", "");
            this.title = m.optString("title", this.name);
            this.titleMy = m.isNull("title_my") ? null : m.optString("title_my", null);
            this.version = m.optString("version", "");
        }
    }

    public static final class PackException extends Exception {
        public PackException(String msg) { super(msg); }
    }

    public interface Progress { void checking(String path, long bytes); }

    private PackVerifier() {}

    public static Pack verify(File dir, byte[] publicKeyPem, boolean hashFiles, Progress progress)
            throws PackException {
        byte[] mb = readSmall(new File(dir, "manifest.json"), "manifest.json not found");
        JSONObject m;
        try {
            m = new JSONObject(new String(mb, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new PackException("manifest.json is not valid JSON");
        }

        verifySignature(dir, publicKeyPem, mb);
        checkManifestJs(dir, mb);

        JSONArray items = m.optJSONArray("items");
        if (items == null) throw new PackException("manifest.json has no items");
        Set<String> paths = new HashSet<String>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) throw new PackException("manifest.json has a broken item");
            String path = it.optString("path", "");
            if (!isSafeContentPath(path)) throw new PackException("unsafe path in manifest: " + path);
            paths.add(path);
            if (!hashFiles) continue;
            File f = new File(dir, path);
            if (!f.isFile()) throw new PackException("missing file: " + path);
            long want = it.optLong("bytes", -1);
            if (want >= 0 && f.length() != want) {
                throw new PackException("HASH MISMATCH: " + path + " (file was changed or damaged)");
            }
            if (progress != null) progress.checking(path, f.length());
            String got;
            try {
                got = sha256Hex(f);
            } catch (IOException e) {
                throw new PackException("cannot read " + path);
            }
            if (!got.equalsIgnoreCase(it.optString("sha256", ""))) {
                throw new PackException("HASH MISMATCH: " + path + " (file was changed or damaged)");
            }
        }
        return new Pack(dir, m, paths);
    }

    static void verifySignature(File dir, byte[] publicKeyPem, byte[] manifestBytes) throws PackException {
        File sf = new File(dir, "manifest.sig");
        if (!sf.isFile()) throw new PackException("the pack is unsigned (manifest.sig missing)");
        byte[] sig;
        try {
            sig = base64(new String(readSmall(sf, "manifest.sig missing"), StandardCharsets.US_ASCII).trim());
        } catch (IllegalArgumentException e) {
            throw new PackException("manifest.sig is not valid base64");
        }
        byte[] raw = rawKeyFromPem(publicKeyPem);
        boolean ok;
        try {
            EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName("Ed25519");
            EdDSAEngine eng = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
            eng.initVerify(new EdDSAPublicKey(new EdDSAPublicKeySpec(raw, spec)));
            eng.update(manifestBytes);
            ok = eng.verify(sig);
        } catch (Exception e) {
            ok = false;
        }
        if (!ok) {
            throw new PackException("SIGNATURE INVALID: the manifest was changed, or it was signed by a different key");
        }
    }

    // DER header of an ed25519 SubjectPublicKeyInfo; the 32 key bytes follow it.
    private static final byte[] SPKI_PREFIX = {
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    /** Reads the 32-byte ed25519 key out of a "-----BEGIN PUBLIC KEY-----" file. */
    public static byte[] rawKeyFromPem(byte[] pem) throws PackException {
        if (pem == null) throw new PackException("no public key");
        String s = new String(pem, StandardCharsets.US_ASCII);
        int a = s.indexOf("-----BEGIN PUBLIC KEY-----");
        int b = s.indexOf("-----END PUBLIC KEY-----");
        if (a < 0 || b < a) throw new PackException("public key file is not valid PEM");
        byte[] der;
        try {
            der = base64(s.substring(a + 26, b).replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw new PackException("public key file is not valid PEM");
        }
        if (der.length != SPKI_PREFIX.length + 32) throw new PackException("public key is not an ed25519 key");
        for (int i = 0; i < SPKI_PREFIX.length; i++) {
            if (der[i] != SPKI_PREFIX[i]) throw new PackException("public key is not an ed25519 key");
        }
        byte[] raw = new byte[32];
        System.arraycopy(der, SPKI_PREFIX.length, raw, 0, 32);
        return raw;
    }

    /** Short name for a key, to compare by eye: the first 16 hex digits of its SHA-256. */
    public static String fingerprint(byte[] pem) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(rawKeyFromPem(pem));
            String h = hex(d).substring(0, 16);
            return h.substring(0, 4) + " " + h.substring(4, 8) + " " + h.substring(8, 12) + " " + h.substring(12);
        } catch (Exception e) {
            return "?";
        }
    }

    static final String MANIFEST_JS_PREFIX = "window.OPENPACK_MANIFEST = ";

    /**
     * manifest.js is the copy the page loads as a script. It must be an exact copy of the signed
     * manifest inside the assignment and nothing else, so no script can ride along with it.
     */
    static void checkManifestJs(File dir, byte[] manifestBytes) throws PackException {
        File f = new File(dir, "manifest.js");
        if (!f.isFile()) throw new PackException("manifest.js is missing");
        String txt = new String(readSmall(f, "manifest.js is missing"), StandardCharsets.UTF_8);
        int end = txt.length();
        while (end > 0 && txt.charAt(end - 1) <= ' ') end--;
        String want = MANIFEST_JS_PREFIX + new String(manifestBytes, StandardCharsets.UTF_8) + ";";
        if (!txt.substring(0, end).equals(want)) throw new PackException("manifest.js does not match manifest.json");
    }

    /** A listed file must be a plain relative path under content/, with no hidden or parent parts. */
    public static boolean isSafeContentPath(String p) {
        if (p == null || p.isEmpty() || p.length() > 1024) return false;
        if (!p.startsWith("content/")) return false;
        if (p.indexOf('\\') >= 0 || p.indexOf(':') >= 0 || p.indexOf('\0') >= 0) return false;
        for (String part : p.split("/", -1)) {
            if (part.isEmpty() || part.startsWith(".")) return false;
        }
        return true;
    }

    public static String sha256Hex(File f) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException(e);
        }
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        } finally {
            in.close();
        }
        return hex(md.digest());
    }

    static String hex(byte[] b) {
        char[] d = "0123456789abcdef".toCharArray();
        char[] out = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            out[2 * i] = d[(b[i] >> 4) & 15];
            out[2 * i + 1] = d[b[i] & 15];
        }
        return new String(out);
    }

    private static byte[] readSmall(File f, String missing) throws PackException {
        if (!f.isFile()) throw new PackException(missing);
        if (f.length() > 64L << 20) throw new PackException(f.getName() + " is too large");
        try {
            InputStream in = new FileInputStream(f);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream((int) f.length());
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            } finally {
                in.close();
            }
        } catch (IOException e) {
            throw new PackException("cannot read " + f.getName());
        }
    }

    /** Standard base64 with padding. Written out here because java.util.Base64 needs Android 8. */
    static byte[] base64(String s) {
        int len = s.length();
        if (len % 4 != 0) throw new IllegalArgumentException("length");
        int pad = 0;
        if (len > 0 && s.charAt(len - 1) == '=') pad++;
        if (len > 1 && s.charAt(len - 2) == '=') pad++;
        byte[] out = new byte[len / 4 * 3 - pad];
        int o = 0;
        for (int i = 0; i < len; i += 4) {
            int v = 0;
            for (int k = 0; k < 4; k++) {
                char c = s.charAt(i + k);
                int d;
                if (c >= 'A' && c <= 'Z') d = c - 'A';
                else if (c >= 'a' && c <= 'z') d = c - 'a' + 26;
                else if (c >= '0' && c <= '9') d = c - '0' + 52;
                else if (c == '+') d = 62;
                else if (c == '/') d = 63;
                else if (c == '=' && i + k >= len - pad) d = 0;
                else throw new IllegalArgumentException("char");
                v = (v << 6) | d;
            }
            if (o < out.length) out[o++] = (byte) (v >> 16);
            if (o < out.length) out[o++] = (byte) (v >> 8);
            if (o < out.length) out[o++] = (byte) v;
        }
        return out;
    }
}
