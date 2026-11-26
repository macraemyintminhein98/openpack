package org.openpack.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Unpacks a pack that arrives as a .zip (from a memory card, Bluetooth, a download).
 *
 * Only the three manifest files and what is under content/ are taken. The page itself
 * (index.html, app.js and so on) is never taken from a zip: those files are not covered by
 * the signature, so the app always uses its own copy.
 */
public final class PackZip {
    private PackZip() {}

    /** Hard stop so a broken or hostile zip cannot fill the phone. */
    public static final long DEFAULT_MAX_BYTES = 64L << 30;

    public static int extract(InputStream zip, File dest, long maxBytes) throws IOException {
        if (!dest.isDirectory() && !dest.mkdirs()) throw new IOException("cannot create folder");
        String root = dest.getCanonicalPath() + File.separator;
        ZipInputStream in = new ZipInputStream(zip);
        byte[] buf = new byte[1 << 16];
        long total = 0;
        int files = 0;
        String prefix = null; // the folder inside the zip that holds manifest.json, if any
        ZipEntry e;
        while ((e = in.getNextEntry()) != null) {
            if (e.isDirectory()) continue;
            String name = e.getName().replace('\\', '/');
            while (name.startsWith("/")) name = name.substring(1);
            String rel = relative(name, prefix);
            if (rel == null && prefix == null) {
                // Allow one wrapping folder: "example-2026.48/manifest.json"
                int slash = name.indexOf('/');
                if (slash > 0 && wanted(name.substring(slash + 1))) {
                    prefix = name.substring(0, slash + 1);
                    rel = name.substring(slash + 1);
                }
            } else if (rel != null && prefix == null) {
                prefix = "";
            }
            if (rel == null) continue;
            File out = new File(dest, rel);
            if (!out.getCanonicalPath().startsWith(root)) throw new IOException("unsafe path in zip: " + name);
            File parent = out.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("cannot create folder");
            OutputStream os = new FileOutputStream(out);
            try {
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > maxBytes) throw new IOException("the pack is too large");
                    os.write(buf, 0, n);
                }
            } finally {
                os.close();
            }
            files++;
        }
        if (!new File(dest, "manifest.json").isFile()) throw new IOException("this file is not a pack (no manifest.json inside)");
        return files;
    }

    private static String relative(String name, String prefix) {
        if (prefix != null) {
            if (!name.startsWith(prefix)) return null;
            name = name.substring(prefix.length());
        }
        return wanted(name) ? name : null;
    }

    static boolean wanted(String rel) {
        if (rel.equals("manifest.json") || rel.equals("manifest.js") || rel.equals("manifest.sig")) return true;
        return PackVerifier.isSafeContentPath(rel);
    }

    public static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
