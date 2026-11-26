package org.openpack.core;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** One "phone" serves the real pack; another copies it over a real socket. */
public class PeerSyncTest {
    private PackServer server;
    private String base;
    private File served, dest;
    private List<byte[]> keys;
    private PackVerifier.Pack servedPack;
    private long totalBytes;

    @Before public void up() throws Exception {
        keys = Collections.singletonList(TestPack.pub());
        served = TestPack.copy();
        servedPack = PackVerifier.verify(served, keys, true, null);
        server = new PackServer(served, served, servedPack.paths, null, "test");
        base = "http://127.0.0.1:" + server.start(false, 0) + "/";
        dest = File.createTempFile("sync", "");
        dest.delete();
        JSONArray items = servedPack.manifest.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) totalBytes += items.getJSONObject(i).getLong("bytes");
    }

    @After public void down() { server.stop(); }

    @Test public void copiesAWholePackAndItVerifies() throws Exception {
        PeerSync.Offer o = PeerSync.look(base, keys);
        assertEquals("example", o.name);
        PeerSync.Result r = PeerSync.fetch(o, dest, null, keys, null);
        assertEquals(servedPack.paths.size(), r.downloaded);
        assertEquals(0, r.reused);
        assertEquals(totalBytes, r.bytesDownloaded);
        PackVerifier.verify(dest, keys, true, null);
        assertFalse("page files are never copied from another phone", new File(dest, "app.js").exists());
    }

    @Test public void onlyCopiesWhatThisPhoneLacks() throws Exception {
        // This phone has an older pack: one file differs, one is missing, the rest are the same.
        File mine = TestPack.copy();
        JSONObject old = new JSONObject(new String(TestPack.read(new File(mine, "manifest.json")), StandardCharsets.UTF_8));
        JSONArray items = old.getJSONArray("items");
        items.getJSONObject(0).put("sha256", "00");
        String gone = items.getJSONObject(1).getString("path");
        items.remove(1);
        PackVerifier.Pack current = new PackVerifier.Pack(mine, old, new HashSet<String>(servedPack.paths));
        PeerSync.Result r = PeerSync.fetch(PeerSync.look(base, keys), dest, current, keys, null);
        assertEquals(2, r.downloaded);
        assertEquals(servedPack.paths.size() - 2, r.reused);
        assertTrue(new File(dest, gone).isFile());
        PackVerifier.verify(dest, keys, true, null);
    }

    @Test public void carriesOnAfterABrokenTransfer() throws Exception {
        PeerSync.Offer o = PeerSync.look(base, keys);
        // What an interrupted attempt leaves behind: the manifest and the first 100 bytes of one file.
        TestPack.write(new File(dest, "manifest.json"), o.manifest);
        byte[] whole = TestPack.read(new File(served, "content/about/welcome.txt"));
        TestPack.write(new File(dest, "content/about/welcome.txt"), Arrays.copyOf(whole, 100));
        PeerSync.Result r = PeerSync.fetch(o, dest, null, keys, null);
        assertEquals("only the missing part was sent again", totalBytes - 100, r.bytesDownloaded);
        assertArrayEquals(whole, TestPack.read(new File(dest, "content/about/welcome.txt")));
        PackVerifier.verify(dest, keys, true, null);
    }

    @Test public void knowsHowMuchSpaceACopyStillNeeds() throws Exception {
        PeerSync.Offer o = PeerSync.look(base, keys);
        assertEquals(totalBytes, PeerSync.bytesStillNeeded(o, dest));
        TestPack.write(new File(dest, "content/about/welcome.txt"), new byte[100]);
        assertEquals(totalBytes - 100, PeerSync.bytesStillNeeded(o, dest));
        PeerSync.fetch(o, dest, null, keys, null);
        assertEquals(0, PeerSync.bytesStillNeeded(o, dest));
    }

    @Test public void throwsAwayLeftoversOfADifferentPack() throws Exception {
        TestPack.write(new File(dest, "manifest.json"), "{\"other\":1}".getBytes());
        TestPack.write(new File(dest, "content/about/stale.txt"), "stale".getBytes());
        PeerSync.fetch(PeerSync.look(base, keys), dest, null, keys, null);
        assertFalse(new File(dest, "content/about/stale.txt").exists());
    }

    @Test public void refusesAPhoneWhoseFilesWereChanged() throws Exception {
        File f = new File(served, "content/about/welcome.txt");
        byte[] b = TestPack.read(f);
        b[5] ^= 1;
        TestPack.write(f, b);
        try {
            PeerSync.fetch(PeerSync.look(base, keys), dest, null, keys, null);
            fail();
        } catch (PackVerifier.PackException e) {
            assertTrue(e.getMessage().contains("HASH MISMATCH"));
        }
        assertFalse("nothing damaged is kept", dest.exists());
    }

    @Test public void refusesAFileLongerThanTheManifestSays() throws Exception {
        File f = new File(served, "content/about/welcome.txt");
        byte[] b = TestPack.read(f);
        TestPack.write(f, Arrays.copyOf(b, b.length + 5000));
        try {
            PeerSync.fetch(PeerSync.look(base, keys), dest, null, keys, null);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("larger than the manifest says"));
        }
    }

    @Test public void refusesAPackSignedByAnotherKeyBeforeCopyingAnything() throws Exception {
        String other = "-----BEGIN PUBLIC KEY-----\nMCowBQYDK2VwAyEA11qYAYKxCrfVS/7TyWQHOg7hcvPapiMlrwIaaPcHURo=\n-----END PUBLIC KEY-----\n";
        try {
            PeerSync.look(base, Collections.singletonList(other.getBytes(StandardCharsets.US_ASCII)));
            fail();
        } catch (PackVerifier.PackException e) {
            assertTrue(e.getMessage().contains("SIGNATURE INVALID"));
        }
        assertFalse(dest.exists());
    }

    @Test public void onlyNearbyAddressesAreEverContacted() throws Exception {
        assertEquals("http://192.168.43.1:8080/", PeerSync.normalize(" 192.168.43.1 "));
        assertEquals("http://10.0.0.5:9000/", PeerSync.normalize("http://10.0.0.5:9000/x/y"));
        assertEquals("http://172.20.1.1:8080/", PeerSync.normalize("172.20.1.1"));
        for (String bad : new String[] {"8.8.8.8", "example.com", "172.32.0.1", "192.169.1.1", "1.2.3", "192.168.1.256",
                "192.168.1.1.evil.com", "https://192.168.1.1", "192.168.1.1:99999", "[::1]", "0x7f.0.0.1", ""}) {
            try {
                PeerSync.normalize(bad);
                fail("accepted " + bad);
            } catch (IOException ok) {}
        }
        try {
            PeerSync.get("http://93.184.216.34/manifest.json", 100, 1000);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("not a nearby address"));
        }
    }

    @Test public void looksAtTheHotspotOwnerFirst() {
        List<String> c = PeerSync.candidates(Arrays.asList("192.168.43.57"));
        assertEquals("192.168.43.1", c.get(0));
        assertEquals(253, c.size());
        assertFalse(c.contains("192.168.43.57"));
        assertTrue(PeerSync.candidates(Arrays.asList("8.8.8.8")).isEmpty());
    }

    @Test public void findsASharingPhone() {
        int port = server.port();
        assertEquals(Arrays.asList("http://127.0.0.1:" + port + "/"), PeerSync.find(Arrays.asList("127.0.0.2", "127.0.0.1"), port, 500));
        server.stop();
        assertTrue(PeerSync.find(Arrays.asList("127.0.0.1"), port, 300).isEmpty());
    }

    @Test public void theAppAndThePackCanBeCarriedAway() throws Exception {
        assertEquals(404, code("openpack.apk"));
        File apk = File.createTempFile("app", ".apk");
        TestPack.write(apk, "PK-pretend-apk".getBytes());
        server.setApk(apk);
        server.setZipName("example-2026.48");
        assertArrayEquals("PK-pretend-apk".getBytes(), PeerSync.get(base + "openpack.apk", 1000, 3000));
        String features = new String(PeerSync.get(base + "features.json", 10000, 3000), StandardCharsets.UTF_8);
        assertTrue(features, features.contains("\"apk\":true") && features.contains("\"pack_zip\":true"));

        // The zip a visitor downloads opens and verifies on another phone.
        byte[] zip = PeerSync.get(base + "pack.zip", 50 << 20, 10000);
        File d = File.createTempFile("unz", "");
        d.delete();
        PackZip.extract(new ByteArrayInputStream(zip), d, PackZip.DEFAULT_MAX_BYTES);
        assertEquals(servedPack.paths, PackVerifier.verify(d, keys, true, null).paths);
    }

    private int code(String path) throws IOException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(base + path).openConnection(java.net.Proxy.NO_PROXY);
        try { return c.getResponseCode(); } finally { c.disconnect(); }
    }

    @Test public void aSpareKeyIsAcceptedAndAStrangerIsNot() throws Exception {
        byte[] other = "-----BEGIN PUBLIC KEY-----\nMCowBQYDK2VwAyEA11qYAYKxCrfVS/7TyWQHOg7hcvPapiMlrwIaaPcHURo=\n-----END PUBLIC KEY-----\n".getBytes(StandardCharsets.US_ASCII);
        PackVerifier.verify(TestPack.PACK, Arrays.asList(other, TestPack.pub()), true, null);
        PackVerifier.verify(TestPack.PACK, Arrays.asList(TestPack.pub(), other), true, null);
        try {
            PackVerifier.verify(TestPack.PACK, Arrays.asList(other, other), true, null);
            fail();
        } catch (PackVerifier.PackException e) {
            assertTrue(e.getMessage().contains("SIGNATURE INVALID"));
        }
    }
}
