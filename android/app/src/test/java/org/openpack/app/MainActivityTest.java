package org.openpack.app;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Starts the real screen on a simulated Android 14 and checks what a first launch must do:
 * unpack the built-in library, verify it, start the server for this phone only, open the page.
 * (The simulated WebView does not draw; the page itself is tested in real browsers.)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivityTest {

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
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
        assertTrue(((Button) a.findViewById(R.id.share)).isEnabled());

        // Second launch reuses the unpacked library and still opens.
        c.pause().stop().destroy();
        Thread.sleep(300);
        MainActivity again = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(waitForPage(again));
    }
}
