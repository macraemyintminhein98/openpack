package org.openpack.app;

import android.content.Context;
import android.content.pm.PackageInfo;

import org.openpack.core.Notices;
import org.openpack.core.PackServer;
import org.openpack.core.PackVerifier;
import org.openpack.core.PackZip;
import org.openpack.core.PeerSync;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The library on this phone: which pack is in use, and the server that shows and shares it.
 * It belongs to the app, not to the screen, so sharing carries on when the screen is closed.
 * Every method here does file or network work: call them off the main thread.
 */
final class Library {
    static final int PORT = 8080;
    private static Library inst;

    static synchronized Library get(Context c) {
        if (inst == null) inst = new Library(c.getApplicationContext());
        return inst;
    }

    static synchronized void resetForTests() {
        if (inst != null) inst.stopServer();
        inst = null;
    }

    private final Context app;
    final File builtinDir, importedDir, incomingDir, syncDir, noticesFile;
    final List<byte[]> keys = new ArrayList<byte[]>();
    String appVersion = "?";
    volatile PackVerifier.Pack pack;
    volatile PackServer server;
    volatile boolean sharing;

    private Library(Context app) {
        this.app = app;
        File f = app.getFilesDir();
        builtinDir = new File(f, "builtin");
        importedDir = new File(f, "imported");
        incomingDir = new File(f, "incoming");
        syncDir = new File(f, "sync");       // an unfinished phone-to-phone copy is kept here so it can carry on
        noticesFile = new File(f, "notices.txt");
    }

    /** Safe to call every time the screen opens. Does the slow work only when it is needed. */
    synchronized void prepare() throws Exception {
        if (pack != null) {
            if (server == null || !server.isRunning()) serve(sharing);
            return;
        }
        PackageInfo pi = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
        appVersion = pi.versionName;
        keys.clear();
        for (int i = 1; i <= 8; i++) {      // keys/1.pub is the everyday key; any others are spares
            try {
                keys.add(readAll(app.getAssets().open("keys/" + i + ".pub")));
            } catch (IOException e) {
                break;
            }
        }
        if (keys.isEmpty()) throw new IOException("this app was built without a public key");

        // Copy the built-in library out of the APK once per installed version.
        String stamp = pi.versionCode + ":" + pi.lastUpdateTime;
        File stampFile = new File(app.getFilesDir(), "builtin.stamp");
        boolean fresh = !stamp.equals(Notices.readText(stampFile)) || !new File(builtinDir, "manifest.json").isFile();
        if (fresh) {
            PackZip.deleteTree(builtinDir);
            copyBuiltin();
        }
        PackVerifier.Pack use = PackVerifier.verify(builtinDir, keys, fresh, null);
        if (fresh) writeText(stampFile, stamp);
        PackZip.deleteTree(incomingDir); // a zip import that did not finish

        if (new File(importedDir, "manifest.json").isFile()) {
            try {
                // Hashed in full when it arrived; other apps cannot change this folder.
                use = PackVerifier.verify(importedDir, keys, false, null);
            } catch (PackVerifier.PackException e) {
                PackZip.deleteTree(importedDir);
            }
        }
        pack = use;
        serve(false);
    }

    synchronized void serve(boolean share) throws IOException {
        stopServer();
        PackServer s = new PackServer(builtinDir, pack.dir, pack.paths, noticesFile, appVersion);
        s.setZipName(pack.name + "-" + pack.version);
        try {
            s.setApk(new File(app.getApplicationInfo().sourceDir)); // the app can hand itself to the next phone
        } catch (Exception ignored) {}
        s.start(share, PORT);
        server = s;
        sharing = share;
    }

    synchronized void stopServer() {
        PackServer s = server;
        server = null;
        if (s != null) s.stop();
    }

    String base() {
        PackServer s = server;
        return "http://127.0.0.1:" + (s == null ? PORT : s.port()) + "/";
    }

    boolean usingImported() {
        PackVerifier.Pack p = pack;
        return p != null && p.dir.equals(importedDir);
    }

    /** A pack that arrived as a .zip file. Nothing changes unless it passes every check. */
    synchronized void importZip(InputStream in) throws Exception {
        PackZip.deleteTree(incomingDir);
        try {
            long room = Math.max(0, app.getFilesDir().getUsableSpace() - (200L << 20));
            PackZip.extract(in, incomingDir, Math.min(room, PackZip.DEFAULT_MAX_BYTES));
            PackVerifier.verify(incomingDir, keys, true, null);
        } catch (Exception e) {
            PackZip.deleteTree(incomingDir);
            throw e;
        }
        adopt(incomingDir);
    }

    /** A pack copied from a nearby phone. An unfinished copy stays in syncDir and carries on next time. */
    synchronized PeerSync.Result syncFrom(PeerSync.Offer offer, PeerSync.Progress progress) throws Exception {
        PeerSync.Result r = PeerSync.fetch(offer, syncDir, pack, keys, progress);
        adopt(syncDir);
        return r;
    }

    /** Puts a fully checked pack folder into use. */
    private void adopt(File checked) throws Exception {
        boolean wasSharing = sharing;
        stopServer();
        File old = new File(app.getFilesDir(), "old");
        PackZip.deleteTree(old);
        if (importedDir.exists() && !importedDir.renameTo(old)) throw new IOException("cannot replace the old pack");
        if (!checked.renameTo(importedDir)) {
            if (old.exists()) old.renameTo(importedDir); // put the old one back
            serve(wasSharing);
            throw new IOException("cannot move the pack into place");
        }
        PackZip.deleteTree(old);
        pack = PackVerifier.verify(importedDir, keys, false, null);
        serve(wasSharing);
    }

    synchronized void useBuiltin() throws Exception {
        boolean wasSharing = sharing;
        stopServer();
        PackZip.deleteTree(importedDir);
        pack = PackVerifier.verify(builtinDir, keys, false, null);
        serve(wasSharing);
    }

    /** The build writes the list of pack files to assets/pack.files, so nothing depends on how a phone lists asset folders. */
    private void copyBuiltin() throws IOException {
        String list = new String(readAll(app.getAssets().open("pack.files")), StandardCharsets.UTF_8);
        String root = builtinDir.getCanonicalPath() + File.separator;
        byte[] buf = new byte[1 << 16];
        for (String rel : list.split("\n")) {
            rel = rel.trim();
            if (rel.isEmpty()) continue;
            File out = new File(builtinDir, rel);
            if (!out.getCanonicalPath().startsWith(root)) throw new IOException("bad file name in the app: " + rel);
            File parent = out.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("cannot create " + parent.getName());
            InputStream in = app.getAssets().open("pack/" + rel);
            try {
                OutputStream os = new FileOutputStream(out);
                try {
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                } finally {
                    os.close();
                }
            } finally {
                in.close();
            }
        }
    }

    static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    static void writeText(File f, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        OutputStream out = new FileOutputStream(f);
        try {
            out.write(b, 0, Math.min(b.length, Notices.MAX_BYTES));
        } finally {
            out.close();
        }
    }
}
