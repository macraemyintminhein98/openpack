package org.openpack.core;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;

public class NoticesTest {
    @Test public void parsesLikeTheGoServer() {
        String bom = String.valueOf((char) 0xFEFF);
        List<String[]> n = Notices.parse(bom + "# comment\r\nClinic\r\nOpen Tuesday\r\n9 to 12\r\n-----\r\n\r\nWater\r\n---\r\n\r\n");
        assertEquals(2, n.size());
        assertEquals("Clinic", n.get(0)[0]);
        assertEquals("Open Tuesday\n9 to 12", n.get(0)[1]);
        assertEquals("Water", n.get(1)[0]);
        assertEquals("", n.get(1)[1]);
    }

    @Test public void emptyAndCapped() {
        assertEquals(0, Notices.parse("").size());
        assertEquals(0, Notices.parse("# only a comment\n---\n").size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) sb.append("n").append(i).append("\n---\n");
        assertEquals(Notices.MAX_NOTICES, Notices.parse(sb.toString()).size());
    }

    @Test public void jsonEscapes() {
        String j = Notices.toJson(Notices.parse("A \"quote\" <b>\nline"));
        assertTrue(j, j.contains("\\\"quote\\\""));
        assertTrue(j.startsWith("{\"notices\":["));
    }
}
