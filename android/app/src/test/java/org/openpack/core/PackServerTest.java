package org.openpack.core;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class PackServerTest {
    private PackServer server;
    private int port;
    private File dir, notices;

    @Before public void up() throws Exception {
        dir = TestPack.copy();
        // Things a server must never hand out, even though they sit in the folder.
        TestPack.write(new File(dir, "content/about/unlisted.txt"), "not in the manifest".getBytes());
        TestPack.write(new File(dir, ".secret"), "x".getBytes());
        notices = new File(dir.getParentFile(), dir.getName() + "-notices.txt");
        TestPack.write(notices, "Clinic\nOpen Tuesday\n---\nWater\n".getBytes());
        PackVerifier.Pack p = PackVerifier.verify(dir, TestPack.pub(), true, null);
        server = new PackServer(dir, dir, p.paths, notices, "test");
        port = server.start(false, 0);
    }

    @After public void down() { server.stop(); }

    /** Sends raw bytes so that odd paths reach the server exactly as written. */
    private String[] raw(String request) throws Exception {
        Socket s = new Socket("127.0.0.1", port);
        try {
            s.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
            s.getOutputStream().flush();
            InputStream in = s.getInputStream();
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            String all = new String(o.toByteArray(), StandardCharsets.ISO_8859_1);
            int cut = all.indexOf("\r\n\r\n");
            return new String[] { all.substring(0, cut), all.substring(cut + 4) };
        } finally { s.close(); }
    }

    private String[] get(String path) throws Exception { return raw("GET " + path + " HTTP/1.1\r\nHost: x\r\n\r\n"); }
    private static int code(String[] r) { return Integer.parseInt(r[0].split(" ")[1]); }

    @Test public void servesThePage() throws Exception {
        String[] r = get("/");
        assertEquals(200, code(r));
        assertTrue(r[0].contains("Content-Type: text/html; charset=utf-8"));
        assertTrue(r[0].contains("X-Content-Type-Options: nosniff"));
        assertTrue(r[0].contains("Cache-Control: no-cache"));
        assertTrue(r[1].contains("app.js"));
        assertEquals(200, code(get("/app.js?v=1")));
        assertEquals(200, code(get("/manifest.json")));
        assertTrue(get("/fonts/noto-sans-myanmar-myanmar-400-normal.woff2")[0].contains("font/woff2"));
    }

    @Test public void servesListedContentByteForByte() throws Exception {
        String[] r = get("/content/about/welcome.txt");
        assertEquals(200, code(r));
        byte[] want = TestPack.read(new File(dir, "content/about/welcome.txt"));
        assertArrayEquals(want, r[1].getBytes(StandardCharsets.ISO_8859_1));
        assertTrue(r[0].contains("Content-Length: " + want.length));
    }

    @Test public void refusesWhatItShouldNotServe() throws Exception {
        assertEquals("not in the signed manifest", 404, code(get("/content/about/unlisted.txt")));
        assertEquals(404, code(get("/.secret")));
        assertEquals(404, code(get("/../" + notices.getName())));
        assertEquals(404, code(get("/content/../../" + notices.getName())));
        assertEquals(404, code(get("/%2e%2e/" + notices.getName())));
        assertEquals(404, code(get("/content/%2e%2e/%2e%2e/" + notices.getName())));
        assertEquals(404, code(get("/..%5c" + notices.getName())));
        assertEquals("no folder listings", 404, code(get("/content/")));
        assertEquals(404, code(get("/content")));
        assertEquals(404, code(get("/fonts/")));
        assertEquals(404, code(get("/nope.html")));
        assertEquals(404, code(get("//etc/passwd")));
    }

    @Test public void readOnly() throws Exception {
        assertEquals(405, code(raw("POST /hub/notices.json HTTP/1.1\r\nContent-Length: 0\r\n\r\n")));
        assertEquals(405, code(raw("PUT /index.html HTTP/1.1\r\nContent-Length: 0\r\n\r\n")));
        assertEquals(405, code(raw("DELETE /manifest.json HTTP/1.1\r\n\r\n")));
    }

    @Test public void headAndRanges() throws Exception {
        byte[] want = TestPack.read(new File(dir, "content/about/welcome.txt"));
        String[] h = raw("HEAD /content/about/welcome.txt HTTP/1.1\r\n\r\n");
        assertEquals(200, code(h));
        assertEquals("", h[1]);
        assertTrue(h[0].contains("Content-Length: " + want.length));

        String[] r = raw("GET /content/about/welcome.txt HTTP/1.1\r\nRange: bytes=10-19\r\n\r\n");
        assertEquals(206, code(r));
        assertTrue(r[0].contains("Content-Range: bytes 10-19/" + want.length));
        assertArrayEquals(Arrays.copyOfRange(want, 10, 20), r[1].getBytes(StandardCharsets.ISO_8859_1));

        r = raw("GET /content/about/welcome.txt HTTP/1.1\r\nRange: bytes=-5\r\n\r\n");
        assertEquals(206, code(r));
        assertArrayEquals(Arrays.copyOfRange(want, want.length - 5, want.length), r[1].getBytes(StandardCharsets.ISO_8859_1));

        r = raw("GET /content/about/welcome.txt HTTP/1.1\r\nRange: bytes=500-\r\n\r\n");
        assertEquals(206, code(r));
        assertEquals(want.length - 500, r[1].length());

        assertEquals(416, code(raw("GET /content/about/welcome.txt HTTP/1.1\r\nRange: bytes=99999-\r\n\r\n")));
    }

    @Test public void featuresAndNotices() throws Exception {
        JSONObject f = new JSONObject(get("/features.json")[1]);
        assertEquals(2, f.getInt("notices"));
        assertEquals(0, f.getJSONArray("urls").length()); // not shared, so no addresses are offered
        assertFalse(f.getBoolean("kiwix"));
        JSONObject n = new JSONObject(new String(get("/hub/notices.json")[1].getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
        assertEquals("Clinic", n.getJSONArray("notices").getJSONObject(0).getString("title"));
        assertEquals("Open Tuesday", n.getJSONArray("notices").getJSONObject(0).getString("body"));
    }

    @Test public void badRequestsDoNotHangOrCrash() throws Exception {
        assertEquals(400, code(raw("GARBAGE\r\n\r\n")));
        assertEquals(400, code(get("/%zz")));
        assertEquals(200, code(get("/"))); // still alive
    }

    @Test public void restartsOnTheSamePortWhenSharingIsSwitched() throws Exception {
        int p1 = port;
        port = server.start(true, p1);
        assertEquals(p1, port);
        assertTrue(server.isShared());
        assertEquals(200, code(get("/")));
        port = server.start(false, p1);
        assertEquals(p1, port);
        assertEquals(200, code(get("/")));
    }

    @Test public void mobileDataLinksAreNotOffered() {
        assertFalse(PackServer.isLocalLink("rmnet_data0"));
        assertFalse(PackServer.isLocalLink("ccmni0"));
        assertFalse(PackServer.isLocalLink("tun0"));
        assertTrue(PackServer.isLocalLink("wlan0"));
        assertTrue(PackServer.isLocalLink("ap0"));
        assertTrue(PackServer.isLocalLink("swlan0"));
    }
}
