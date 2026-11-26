package org.openpack.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.DownloadListener;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import org.openpack.core.Notices;
import org.openpack.core.PackServer;
import org.openpack.core.PackVerifier;
import org.openpack.core.PackZip;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One screen: the library page in a WebView, served by a small server inside the app.
 * "Share" opens that server to phones on the same Wi-Fi or hotspot.
 * The app never contacts the internet and records nothing about what is read.
 */
public class MainActivity extends Activity {
    private static final int REQ_OPEN_PACK = 1, REQ_SAVE_FILE = 2;
    private static final int PORT = 8080;

    private WebView web;
    private TextView status;
    private Button shareBtn;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private File builtinDir, importedDir, incomingDir, noticesFile;
    private byte[] publicKey;
    private String appVersion = "?";
    private volatile PackVerifier.Pack pack;
    private volatile PackServer server;
    private volatile boolean sharing;
    private File pendingSave;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.main);
        web = (WebView) findViewById(R.id.web);
        status = (TextView) findViewById(R.id.status);
        shareBtn = (Button) findViewById(R.id.share);
        Button more = (Button) findViewById(R.id.more);

        builtinDir = new File(getFilesDir(), "builtin");
        importedDir = new File(getFilesDir(), "imported");
        incomingDir = new File(getFilesDir(), "incoming");
        noticesFile = new File(getFilesDir(), "notices.txt");

        setUpWebView();
        shareBtn.setEnabled(false);
        shareBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleShare(); }
        });
        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(v); }
        });

        showStatus(getString(R.string.checking));
        io.execute(new Runnable() {
            @Override public void run() { prepare(); }
        });
    }

    // ------------------------------------------------------------ start-up

    private void prepare() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            appVersion = pi.versionName;
            publicKey = readAll(getAssets().open("openpack.pub"));

            // Copy the built-in library out of the APK once per installed version.
            String stamp = pi.versionCode + ":" + pi.lastUpdateTime;
            File stampFile = new File(getFilesDir(), "builtin.stamp");
            boolean fresh = !stamp.equals(Notices.readText(stampFile)) || !new File(builtinDir, "manifest.json").isFile();
            if (fresh) {
                PackZip.deleteTree(builtinDir);
                copyBuiltin(builtinDir);
            }
            PackVerifier.Pack builtin = PackVerifier.verify(builtinDir, publicKey, fresh, null);
            if (fresh) writeText(stampFile, stamp);
            PackZip.deleteTree(incomingDir); // left over from an import that did not finish

            PackVerifier.Pack use = builtin;
            if (new File(importedDir, "manifest.json").isFile()) {
                try {
                    // Hashed in full when it was imported; app storage cannot be changed by other apps.
                    use = PackVerifier.verify(importedDir, publicKey, false, null);
                } catch (PackVerifier.PackException e) {
                    PackZip.deleteTree(importedDir);
                }
            }
            pack = use;
            startServer(false);
            ui(new Runnable() {
                @Override public void run() {
                    hideStatus();
                    shareBtn.setEnabled(true);
                    web.loadUrl(base());
                }
            });
        } catch (final Exception e) {
            ui(new Runnable() {
                @Override public void run() { showStatus(getString(R.string.library_bad, String.valueOf(e.getMessage()))); }
            });
        }
    }

    /** Runs on the io thread. */
    private void startServer(boolean share) throws IOException {
        PackServer old = server;
        if (old != null) old.stop();
        PackServer s = new PackServer(builtinDir, pack.dir, pack.paths, noticesFile, appVersion);
        s.start(share, PORT);
        server = s;
        sharing = share;
    }

    private String base() {
        PackServer s = server;
        return "http://127.0.0.1:" + (s == null ? PORT : s.port()) + "/";
    }

    // ------------------------------------------------------------ the page

    private void setUpWebView() {
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);          // the library page is a script; it is the app's own copy
        ws.setDomStorageEnabled(true);          // remembers language and text size
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setGeolocationEnabled(false);
        ws.setSupportMultipleWindows(false);
        ws.setMediaPlaybackRequiresUserGesture(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return blockOutside(r.getUrl());
            }
            @SuppressWarnings("deprecation")
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return blockOutside(Uri.parse(url));
            }
        });
        // Files the page cannot show itself (PDF, APK, "Save" buttons) are saved where the person chooses.
        web.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                saveFromUrl(Uri.parse(url));
            }
        });
    }

    /** Only the app's own server may be opened. Anything else would need the internet. */
    private boolean blockOutside(Uri u) {
        PackServer s = server;
        boolean ours = u != null && "http".equals(u.getScheme()) && "127.0.0.1".equals(u.getHost())
            && s != null && u.getPort() == s.port();
        if (!ours) toast(getString(R.string.offline_link));
        return !ours;
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    // ------------------------------------------------------------ sharing

    private void toggleShare() {
        final boolean want = !sharing;
        shareBtn.setEnabled(false);
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    if (pack == null) return;
                    final List<String> urls = want ? PackServer.lanUrls(PORT) : null;
                    if (want && urls.isEmpty()) {
                        ui(new Runnable() {
                            @Override public void run() { showStatus(getString(R.string.no_network)); }
                        });
                        return;
                    }
                    startServer(want);
                    final List<String> real = want ? PackServer.lanUrls(server.port()) : null;
                    ui(new Runnable() {
                        @Override public void run() {
                            if (want) {
                                StringBuilder sb = new StringBuilder();
                                for (String u : real) sb.append(sb.length() > 0 ? "   " : "").append(u);
                                showStatus(getString(R.string.sharing_on, sb.toString()));
                                shareBtn.setText(R.string.share_stop);
                                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                web.loadUrl(base() + "#/share");
                            } else {
                                hideStatus();
                                shareBtn.setText(R.string.share);
                                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                            }
                        }
                    });
                } catch (final IOException e) {
                    ui(new Runnable() {
                        @Override public void run() { showStatus(String.valueOf(e.getMessage())); }
                    });
                } finally {
                    ui(new Runnable() {
                        @Override public void run() { shareBtn.setEnabled(pack != null); }
                    });
                }
            }
        });
    }

    // ------------------------------------------------------------ menu

    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, R.string.open_pack);
        m.getMenu().add(0, 2, 1, R.string.edit_notices);
        if (pack != null && pack.dir.equals(importedDir)) m.getMenu().add(0, 3, 2, R.string.use_builtin);
        m.getMenu().add(0, 4, 3, R.string.about);
        m.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case 1: pickPack(); return true;
                    case 2: editNotices(); return true;
                    case 3: useBuiltin(); return true;
                    case 4: about(); return true;
                    default: return false;
                }
            }
        });
        m.show();
    }

    private void pickPack() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*"); // phones disagree about what type a .zip is
        try {
            startActivityForResult(i, REQ_OPEN_PACK);
        } catch (Exception e) {
            toast(String.valueOf(e.getMessage()));
        }
    }

    private void importPack(final Uri uri) {
        showStatus(getString(R.string.checking));
        io.execute(new Runnable() {
            @Override public void run() {
                String problem = null;
                try {
                    PackZip.deleteTree(incomingDir);
                    long room = Math.max(0, getFilesDir().getUsableSpace() - (200L << 20));
                    InputStream in = getContentResolver().openInputStream(uri);
                    if (in == null) throw new IOException("cannot open the file");
                    try {
                        PackZip.extract(in, incomingDir, Math.min(room, PackZip.DEFAULT_MAX_BYTES));
                    } finally {
                        in.close();
                    }
                    // Signature first, then every file's size and SHA-256. Nothing is used before this passes.
                    PackVerifier.verify(incomingDir, publicKey, true, null);
                    boolean wasSharing = sharing;
                    PackServer old = server;
                    if (old != null) old.stop();
                    PackZip.deleteTree(importedDir);
                    if (!incomingDir.renameTo(importedDir)) throw new IOException("cannot move the pack into place");
                    pack = PackVerifier.verify(importedDir, publicKey, false, null);
                    startServer(wasSharing);
                } catch (Exception e) {
                    problem = String.valueOf(e.getMessage());
                    PackZip.deleteTree(incomingDir);
                    try {
                        if (server == null || !server.isRunning()) {
                            if (!new File(pack.dir, "manifest.json").isFile()) {
                                pack = PackVerifier.verify(builtinDir, publicKey, false, null);
                            }
                            startServer(sharing);
                        }
                    } catch (Exception ignored) {}
                }
                final String p = problem;
                ui(new Runnable() {
                    @Override public void run() {
                        if (p == null) {
                            if (!sharing) hideStatus();
                            toast(getString(R.string.pack_ok));
                            web.clearHistory();
                            web.loadUrl(base());
                        } else {
                            showStatus(getString(R.string.pack_bad, p));
                        }
                    }
                });
            }
        });
    }

    private void useBuiltin() {
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    PackServer old = server;
                    if (old != null) old.stop();
                    PackZip.deleteTree(importedDir);
                    pack = PackVerifier.verify(builtinDir, publicKey, false, null);
                    startServer(sharing);
                    ui(new Runnable() {
                        @Override public void run() { web.clearHistory(); web.loadUrl(base()); }
                    });
                } catch (final Exception e) {
                    ui(new Runnable() {
                        @Override public void run() { showStatus(getString(R.string.library_bad, String.valueOf(e.getMessage()))); }
                    });
                }
            }
        });
    }

    private void editNotices() {
        final EditText box = new EditText(this);
        box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.setMinLines(8);
        box.setGravity(Gravity.TOP | Gravity.START);
        box.setHint(R.string.notices_hint);
        box.setText(Notices.readText(noticesFile));
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
            .setTitle(R.string.edit_notices)
            .setView(box)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    try {
                        writeText(noticesFile, box.getText().toString());
                        toast(getString(R.string.saved));
                        web.reload();
                    } catch (IOException e) {
                        toast(getString(R.string.save_fail));
                    }
                }
            })
            .show();
    }

    private void about() {
        PackVerifier.Pack p = pack;
        String title = p == null ? "?" : (p.titleMy != null && "my".equals(getResources().getConfiguration().locale.getLanguage()) ? p.titleMy : p.title);
        String text = getString(R.string.about_text, title, p == null ? "?" : p.version,
            appVersion + " (" + getString(R.string.test_build) + ")", PackVerifier.fingerprint(publicKey));
        new AlertDialog.Builder(this).setTitle(R.string.app_name).setMessage(text)
            .setPositiveButton(R.string.ok, null).show();
    }

    // ------------------------------------------------------------ saving files

    private void saveFromUrl(Uri u) {
        PackServer s = server;
        File f = s == null || u == null ? null : s.resolve(u.getPath());
        if (f == null) { toast(getString(R.string.save_fail)); return; }
        pendingSave = f;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(PackServer.mime(f.getName()).split(";")[0]);
        i.putExtra(Intent.EXTRA_TITLE, f.getName());
        try {
            startActivityForResult(i, REQ_SAVE_FILE);
        } catch (Exception e) {
            toast(getString(R.string.save_fail));
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (req == REQ_OPEN_PACK) {
            importPack(uri);
        } else if (req == REQ_SAVE_FILE && pendingSave != null) {
            final File src = pendingSave;
            pendingSave = null;
            io.execute(new Runnable() {
                @Override public void run() {
                    boolean ok = false;
                    try {
                        InputStream in = new FileInputStream(src);
                        try {
                            OutputStream out = getContentResolver().openOutputStream(uri);
                            if (out != null) {
                                try {
                                    byte[] buf = new byte[1 << 16];
                                    int n;
                                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                                    ok = true;
                                } finally {
                                    out.close();
                                }
                            }
                        } finally {
                            in.close();
                        }
                    } catch (Exception ignored) {}
                    final boolean done = ok;
                    ui(new Runnable() {
                        @Override public void run() { toast(getString(done ? R.string.saved : R.string.save_fail)); }
                    });
                }
            });
        }
    }

    // ------------------------------------------------------------ helpers

    /**
     * Copies the built-in library out of the APK. The build writes the list of its files to
     * assets/pack.files, so nothing depends on how a phone lists asset folders.
     */
    private void copyBuiltin(File dest) throws IOException {
        String list = new String(readAll(getAssets().open("pack.files")), StandardCharsets.UTF_8);
        String root = dest.getCanonicalPath() + File.separator;
        byte[] buf = new byte[1 << 16];
        for (String rel : list.split("\n")) {
            rel = rel.trim();
            if (rel.isEmpty()) continue;
            File out = new File(dest, rel);
            if (!out.getCanonicalPath().startsWith(root)) throw new IOException("bad file name in the app: " + rel);
            File parent = out.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("cannot create " + parent.getName());
            InputStream in = getAssets().open("pack/" + rel);
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

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static void writeText(File f, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        OutputStream out = new FileOutputStream(f);
        try {
            out.write(b, 0, Math.min(b.length, Notices.MAX_BYTES));
        } finally {
            out.close();
        }
    }

    private void ui(Runnable r) { if (!isFinishing()) runOnUiThread(r); }
    private void showStatus(String s) { status.setText(s); status.setVisibility(View.VISIBLE); }
    private void hideStatus() { status.setVisibility(View.GONE); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    @Override
    protected void onDestroy() {
        final PackServer s = server;
        server = null;
        io.execute(new Runnable() {
            @Override public void run() { if (s != null) s.stop(); }
        });
        io.shutdown();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
