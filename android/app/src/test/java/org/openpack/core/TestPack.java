package org.openpack.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** The real pack built by the Node CLI (scripts/build-android.sh copies it into assets). */
final class TestPack {
    static final File PACK = new File("src/main/assets/pack");
    static final File PUB = new File("src/main/assets/keys/1.pub");

    static byte[] pub() throws IOException { return read(PUB); }

    static byte[] read(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            return o.toByteArray();
        } finally { in.close(); }
    }

    static void write(File f, byte[] b) throws IOException {
        f.getParentFile().mkdirs();
        OutputStream o = new FileOutputStream(f);
        try { o.write(b); } finally { o.close(); }
    }

    static File copy() throws IOException {
        File d = File.createTempFile("pack", "");
        d.delete();
        copyTree(PACK, d);
        return d;
    }

    static void copyTree(File a, File b) throws IOException {
        if (a.isDirectory()) {
            b.mkdirs();
            for (File k : a.listFiles()) copyTree(k, new File(b, k.getName()));
        } else {
            write(b, read(a));
        }
    }
}
