package org.openpack.core;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class PackVerifierTest {
    private static void fails(File dir, byte[] pub, String expect) throws Exception {
        try {
            PackVerifier.verify(dir, pub, true, null);
            fail("expected: " + expect);
        } catch (PackVerifier.PackException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(expect));
        }
    }

    @Test public void acceptsThePackBuiltByTheNodeTool() throws Exception {
        PackVerifier.Pack p = PackVerifier.verify(TestPack.PACK, TestPack.pub(), true, null);
        assertEquals("example", p.name);
        assertTrue(p.paths.size() >= 10);
        assertTrue(p.paths.contains("content/about/welcome.txt"));
        int lessons = 0;
        for (String s : p.paths) if (s.endsWith(".lesson.js")) lessons++;
        assertEquals("all nine survival lessons are in the pack", 9, lessons);
    }

    @Test public void rejectsAChangedContentFile() throws Exception {
        File d = TestPack.copy();
        File f = new File(d, "content/about/welcome.txt");
        byte[] b = TestPack.read(f);
        b[0] ^= 1; // same size, one bit different
        TestPack.write(f, b);
        fails(d, TestPack.pub(), "HASH MISMATCH");
    }

    @Test public void rejectsAMissingFile() throws Exception {
        File d = TestPack.copy();
        assertTrue(new File(d, "content/about/welcome.txt").delete());
        fails(d, TestPack.pub(), "missing file");
    }

    @Test public void rejectsAChangedManifest() throws Exception {
        File d = TestPack.copy();
        File f = new File(d, "manifest.json");
        String s = new String(TestPack.read(f), StandardCharsets.UTF_8).replace("Welcome to OpenPack", "Welcome to 0penPack");
        TestPack.write(f, s.getBytes(StandardCharsets.UTF_8));
        fails(d, TestPack.pub(), "SIGNATURE INVALID");
    }

    @Test public void rejectsAPackSignedByAnotherKey() throws Exception {
        // A valid ed25519 public key that did not sign this pack (RFC 8032 test vector 1).
        String other = "-----BEGIN PUBLIC KEY-----\nMCowBQYDK2VwAyEA11qYAYKxCrfVS/7TyWQHOg7hcvPapiMlrwIaaPcHURo=\n-----END PUBLIC KEY-----\n";
        fails(TestPack.PACK, other.getBytes(StandardCharsets.US_ASCII), "SIGNATURE INVALID");
    }

    @Test public void rejectsAnUnsignedPack() throws Exception {
        File d = TestPack.copy();
        assertTrue(new File(d, "manifest.sig").delete());
        fails(d, TestPack.pub(), "unsigned");
    }

    @Test public void rejectsAManifestJsThatSaysSomethingElse() throws Exception {
        File d = TestPack.copy();
        File f = new File(d, "manifest.js");
        String s = new String(TestPack.read(f), StandardCharsets.UTF_8).replace("Welcome to OpenPack", "Free money");
        TestPack.write(f, s.getBytes(StandardCharsets.UTF_8));
        fails(d, TestPack.pub(), "does not match");
    }

    @Test public void rejectsScriptHiddenInManifestJs() throws Exception {
        File d = TestPack.copy();
        File f = new File(d, "manifest.js");
        String s = new String(TestPack.read(f), StandardCharsets.UTF_8) + "\nfetch('http://x/');var a={}";
        TestPack.write(f, s.getBytes(StandardCharsets.UTF_8));
        fails(d, TestPack.pub(), "does not match");
        // Script with no braces at all, before the assignment.
        String good = new String(TestPack.read(new File(TestPack.PACK, "manifest.js")), StandardCharsets.UTF_8);
        TestPack.write(f, ("fetch('http://x/');" + good).getBytes(StandardCharsets.UTF_8));
        fails(d, TestPack.pub(), "does not match");
        TestPack.write(f, (good.trim() + "fetch('http://x/')").getBytes(StandardCharsets.UTF_8));
        fails(d, TestPack.pub(), "does not match");
    }

    @Test public void rejectsBadKeys() throws Exception {
        fails(TestPack.PACK, "hello".getBytes(), "not valid PEM");
        String rsaLike = "-----BEGIN PUBLIC KEY-----\nMCowBQYDK2VwAyEA\n-----END PUBLIC KEY-----\n";
        fails(TestPack.PACK, rsaLike.getBytes(), "not an ed25519 key");
    }

    @Test public void pathRules() {
        assertTrue(PackVerifier.isSafeContentPath("content/health/a b.pdf"));
        assertFalse(PackVerifier.isSafeContentPath("content/../app.js"));
        assertFalse(PackVerifier.isSafeContentPath("content/.hidden"));
        assertFalse(PackVerifier.isSafeContentPath("app.js"));
        assertFalse(PackVerifier.isSafeContentPath("/content/a"));
        assertFalse(PackVerifier.isSafeContentPath("content//a"));
        assertFalse(PackVerifier.isSafeContentPath("content\\a"));
        assertFalse(PackVerifier.isSafeContentPath("content/a/"));
    }

    @Test public void base64() {
        assertArrayEquals("hello".getBytes(), PackVerifier.base64("aGVsbG8="));
        assertArrayEquals("hi".getBytes(), PackVerifier.base64("aGk="));
        assertArrayEquals(new byte[0], PackVerifier.base64(""));
        try { PackVerifier.base64("a=bc"); fail(); } catch (IllegalArgumentException ok) {}
    }

    @Test public void fingerprintIsStable() throws Exception {
        String a = PackVerifier.fingerprint(TestPack.pub());
        assertEquals(19, a.length());
        assertEquals(a, PackVerifier.fingerprint(TestPack.pub()));
    }
}
