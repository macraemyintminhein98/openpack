package org.openpack.app;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openpack.core.PackServer;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Runs the real app code on simulated Android versions: the oldest it supports, a middle one,
 * and Android 14. (The simulated WebView does not draw; the page itself is tested in real browsers.)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {21, 28, 34})
public class MainActivityTest {

    @After public void reset() { Library.resetForTests(); }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(Proxy.NO_PROXY);
        c.setConnectTimeout(3000);
        c.setReadTimeout(5000);
        assertEquals(url, 200, c.getResponseCode());
        InputStream in = c.getInputStream();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        return new String(o.toByteArray(), "UTF-8");
    }

    private static String waitForPage(MainActivity a) throws Exception {
        WebView web = (WebView) a.findViewById(R.id.web);
        for (int i = 0; i < 300; i++) {
            shadowOf(Looper.getMainLooper()).idle();
            String u = shadowOf(web).getLastLoadedUrl();
            if (u != null) return u;
            Thread.sleep(100);
        }
        TextView status = (TextView) a.findViewById(R.id.status);
        fail("the page never opened; status: " + status.getText());
        return null;
    }

    @Test public void firstLaunchUnpacksVerifiesServesAndOpensThePage() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        String url = waitForPage(a);
        assertTrue(url, url.matches("http://127\\.0\\.0\\.1:808\\d/"));

        File builtin = new File(a.getFilesDir(), "builtin");
        assertTrue(new File(builtin, "index.html").isFile());
        assertTrue(new File(builtin, "manifest.sig").isFile());
        assertTrue(new File(builtin, "fonts/noto-sans-myanmar-myanmar-400-normal.woff2").isFile());

        assertTrue(get(url).contains("app.js"));
        assertTrue(get(url + "manifest.json").contains("\"items\""));
        String features = get(url + "features.json");
        assertTrue(features, features.contains("\"urls\":[]")); // not shared until the person taps Share
        assertTrue(get(url + "content/survival/water-safe.lesson.js").contains("OPENPACK_LESSONS"));

        assertEquals(View.GONE, a.findViewById(R.id.status).getVisibility());
        Button share = (Button) a.findViewById(R.id.share);
        assertTrue(share.isEnabled());
        assertNotNull("the app's own Burmese font is on the buttons", share.getTypeface());
        assertNotEquals(android.graphics.Typeface.DEFAULT, share.getTypeface());

        // The screen is closed and opened again: the unpacked library is reused.
        c.pause().stop().destroy();
        Thread.sleep(300);
        MainActivity again = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(waitForPage(again));
    }

    @Test public void sharingOutlivesTheScreenAndStopsWhenToldTo() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        String url = waitForPage(c.get());
        Library lib = Library.get(RuntimeEnvironment.getApplication());
        lib.serve(true);

        ServiceController<ShareService> sc = Robolectric.buildService(ShareService.class).create().startCommand(0, 1);
        Notification n = shadowOf(sc.get()).getLastForegroundNotification();
        assertNotNull("Android is shown a notice, which is what lets sharing carry on", n);

        c.pause().stop().destroy();          // the person leaves the app
        Thread.sleep(400);
        assertTrue(lib.sharing);
        assertTrue(get(url + "features.json").contains("\"app\":\"android\"")); // still answering

        Intent stop = new Intent(RuntimeEnvironment.getApplication(), ShareService.class).setAction(ShareService.STOP);
        Robolectric.buildService(ShareService.class, stop).create().startCommand(0, 2);
        for (int i = 0; i < 50 && lib.sharing; i++) Thread.sleep(100);
        assertFalse("Stop in the notice switches sharing off", lib.sharing);
        PackServer s = lib.server;
        assertTrue(s != null && s.isRunning() && !s.isShared());
    }

    @Test public void aPackFromAZipReplacesTheLibraryOnlyIfItChecksOut() throws Exception {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        String url = waitForPage(a);
        Library lib = Library.get(a);
        assertFalse(lib.usingImported());

        ByteArrayOutputStream good = new ByteArrayOutputStream(), bad = new ByteArrayOutputStream();
        zip(lib.builtinDir, good, false);
        zip(lib.builtinDir, bad, true);

        try {
            lib.importZip(new ByteArrayInputStream(bad.toByteArray()));
            fail("a changed pack was accepted");
        } catch (Exception e) {
            assertTrue(e.getMessage(), e.getMessage().contains("HASH MISMATCH"));
        }
        assertFalse(lib.usingImported());
        assertTrue("the old library keeps working after a refused pack", get(url).contains("app.js"));

        lib.importZip(new ByteArrayInputStream(good.toByteArray()));
        assertTrue(lib.usingImported());
        assertTrue(get(url + "content/about/welcome.txt").length() > 100);
        assertFalse(new File(lib.importedDir, "app.js").exists());

        lib.useBuiltin();
        assertFalse(lib.usingImported());
        assertFalse(lib.importedDir.exists());
    }

    private static void zip(File dir, ByteArrayOutputStream out, boolean damage) throws Exception {
        ZipOutputStream z = new ZipOutputStream(out);
        add(z, dir, "", damage);
        z.close();
    }

    private static void add(ZipOutputStream z, File f, String name, boolean damage) throws Exception {
        if (f.isDirectory()) {
            for (File k : f.listFiles()) add(z, k, name.isEmpty() ? k.getName() : name + "/" + k.getName(), damage);
            return;
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        InputStream in = new FileInputStream(f);
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close();
        byte[] data = o.toByteArray();
        if (damage && name.equals("content/about/welcome.txt")) data[3] ^= 1;
        z.putNextEntry(new ZipEntry(name));
        z.write(data);
        z.closeEntry();
    }
}
