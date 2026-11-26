package org.openpack.core;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PackZipTest {
    private static void add(ZipOutputStream z, File f, String name) throws IOException {
        if (f.isDirectory()) {
            for (File k : f.listFiles()) add(z, k, name.isEmpty() ? k.getName() : name + "/" + k.getName());
        } else {
            z.putNextEntry(new ZipEntry(name));
            z.write(TestPack.read(f));
            z.closeEntry();
        }
    }

    private static byte[] zipOfPack(String prefix, String[][] extra) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ZipOutputStream z = new ZipOutputStream(b);
        add(z, TestPack.PACK, prefix);
        if (extra != null) for (String[] e : extra) {
            z.putNextEntry(new ZipEntry(e[0]));
            z.write(e[1].getBytes());
            z.closeEntry();
        }
        z.close();
        return b.toByteArray();
    }

    private static File tmp() throws IOException {
        File d = File.createTempFile("zip", "");
        d.delete();
        return d;
    }

    @Test public void importsAZippedPackAndItVerifies() throws Exception {
        File d = tmp();
        PackZip.extract(new ByteArrayInputStream(zipOfPack("", null)), d, PackZip.DEFAULT_MAX_BYTES);
        PackVerifier.verify(d, TestPack.pub(), true, null);
        assertFalse("the page is never taken from a zip", new File(d, "app.js").exists());
        assertFalse(new File(d, "index.html").exists());
    }

    @Test public void acceptsOneWrappingFolder() throws Exception {
        File d = tmp();
        PackZip.extract(new ByteArrayInputStream(zipOfPack("example-2026.48", null)), d, PackZip.DEFAULT_MAX_BYTES);
        PackVerifier.verify(d, TestPack.pub(), true, null);
    }

    @Test public void ignoresEntriesThatTryToEscape() throws Exception {
        File d = tmp();
        byte[] zip = zipOfPack("", new String[][] {
            {"../evil.txt", "x"}, {"content/../../evil2.txt", "x"}, {"/etc/evil3", "x"}, {"content/.hidden", "x"}
        });
        PackZip.extract(new ByteArrayInputStream(zip), d, PackZip.DEFAULT_MAX_BYTES);
        assertFalse(new File(d.getParentFile(), "evil.txt").exists());
        assertFalse(new File(d.getParentFile(), "evil2.txt").exists());
        assertFalse(new File(d, "content/.hidden").exists());
        PackVerifier.verify(d, TestPack.pub(), true, null);
    }

    @Test public void stopsAtTheSizeLimit() throws Exception {
        try {
            PackZip.extract(new ByteArrayInputStream(zipOfPack("", null)), tmp(), 1000);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("too large"));
        }
    }

    @Test public void refusesAZipThatIsNotAPack() throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ZipOutputStream z = new ZipOutputStream(b);
        z.putNextEntry(new ZipEntry("photo.jpg"));
        z.write(1);
        z.close();
        try {
            PackZip.extract(new ByteArrayInputStream(b.toByteArray()), tmp(), 1 << 20);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("not a pack"));
        }
    }
}
