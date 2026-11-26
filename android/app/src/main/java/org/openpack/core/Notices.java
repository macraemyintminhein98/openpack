package org.openpack.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The notice board. Same file format as the Go server (server/hub.go): notices are separated by
 * a line of three or more dashes, the first line of each is its title, lines starting with # are
 * ignored. Visitors can only read it.
 */
public final class Notices {
    public static final int MAX_NOTICES = 30;
    public static final int MAX_BYTES = 64 * 1024;

    private Notices() {}

    /** Each entry is {title, body}. */
    public static List<String[]> parse(String text) {
        List<String[]> out = new ArrayList<String[]>();
        if (text == null) return out;
        if (!text.isEmpty() && text.charAt(0) == (char) 0xFEFF) text = text.substring(1);
        text = text.replace("\r\n", "\n");
        List<String> cur = new ArrayList<String>();
        for (String line : text.split("\n", -1)) {
            String t = line.trim();
            if (t.startsWith("#")) continue;
            if (t.length() >= 3 && t.replace("-", "").isEmpty()) {
                flush(cur, out);
                continue;
            }
            cur.add(line);
        }
        flush(cur, out);
        return out;
    }

    private static void flush(List<String> cur, List<String[]> out) {
        while (!cur.isEmpty() && cur.get(0).trim().isEmpty()) cur.remove(0);
        if (!cur.isEmpty() && out.size() < MAX_NOTICES) {
            StringBuilder body = new StringBuilder();
            for (int i = 1; i < cur.size(); i++) {
                if (i > 1) body.append('\n');
                body.append(cur.get(i));
            }
            out.add(new String[] { cur.get(0).trim(), body.toString().trim() });
        }
        cur.clear();
    }

    public static String readText(File f) {
        if (f == null || !f.isFile()) return "";
        try {
            InputStream in = new FileInputStream(f);
            try {
                byte[] buf = new byte[MAX_BYTES];
                int n = 0, r;
                while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
                return new String(buf, 0, n, StandardCharsets.UTF_8);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return "";
        }
    }

    public static List<String[]> read(File f) { return parse(readText(f)); }

    public static String toJson(List<String[]> notices) {
        try {
            JSONArray arr = new JSONArray();
            for (String[] n : notices) {
                JSONObject o = new JSONObject();
                o.put("title", n[0]);
                o.put("body", n[1]);
                arr.put(o);
            }
            return new JSONObject().put("notices", arr).toString();
        } catch (Exception e) {
            return "{\"notices\":[]}";
        }
    }
}
