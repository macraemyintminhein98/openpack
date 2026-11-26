package org.openpack.core;

import java.io.File;

/**
 * Runs the app's server on a computer, so the browser tests (scripts/browser-test.py with
 * OP_SERVER set) can drive the real page against the same code the phone runs.
 * Arguments are the Go server's: -dir <pack> -pub <key> -port <n>
 */
public final class ServeMain {
    public static void main(String[] a) throws Exception {
        File dir = null, pub = null;
        int port = 8080;
        for (int i = 0; i + 1 < a.length; i += 2) {
            if (a[i].equals("-dir")) dir = new File(a[i + 1]);
            if (a[i].equals("-pub")) pub = new File(a[i + 1]);
            if (a[i].equals("-port")) port = Integer.parseInt(a[i + 1]);
        }
        PackVerifier.Pack p = PackVerifier.verify(dir, TestPack.read(pub), true, null);
        // (the browser tests pass the bundle's key with -pub)
        File notices = new File(dir.getCanonicalFile().getParentFile(), "notices.txt");
        PackServer s = new PackServer(dir, dir, p.paths, notices, "android-test");
        System.out.println("serving " + p.name + " on " + s.start(true, port));
        Thread.sleep(Long.MAX_VALUE);
    }
}
