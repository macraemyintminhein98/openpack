package org.openpack.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.DownloadListener;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import org.openpack.core.Notices;
import org.openpack.core.PackServer;
import org.openpack.core.PackVerifier;
import org.openpack.core.PeerSync;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
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

    private WebView web;
    private TextView status;
    private Button shareBtn;
    private Typeface font, fontBold;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Library lib;
    private File pendingSave;
    private boolean busy;

    private final BroadcastReceiver stopped = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            main.postDelayed(new Runnable() {
                @Override public void run() { showSharing(); }
            }, 700);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.main);
        lib = Library.get(this);
        web = (WebView) findViewById(R.id.web);
        status = (TextView) findViewById(R.id.status);
        shareBtn = (Button) findViewById(R.id.share);
        Button more = (Button) findViewById(R.id.more);
        try {
            // Many phones in Myanmar still have a Zawgyi system font, which scrambles Unicode Burmese.
            // The app carries its own font so its buttons always read correctly.
            font = Typeface.createFromAsset(getAssets(), "fonts/NotoSansMyanmar-Regular.ttf");
            fontBold = Typeface.createFromAsset(getAssets(), "fonts/NotoSansMyanmar-Bold.ttf");
        } catch (Exception e) {
            font = Typeface.DEFAULT;
            fontBold = Typeface.DEFAULT_BOLD;
        }
        shareBtn.setTypeface(font);
        more.setTypeface(font);
        status.setTypeface(font);

        setUpWebView();
        shareBtn.setEnabled(false);
        shareBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleShare(); }
        });
        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(); }
        });
        IntentFilter f = new IntentFilter(ShareService.STOP);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(stopped, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(stopped, f);

        showStatus(getString(R.string.checking));
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    lib.prepare();
                    ui(new Runnable() {
                        @Override public void run() {
                            hideStatus();
                            shareBtn.setEnabled(true);
                            showSharing();
                            web.loadUrl(lib.base());
                        }
                    });
                } catch (final Exception e) {
                    ui(new Runnable() {
                        @Override public void run() { showStatus(getString(R.string.library_bad, String.valueOf(e.getMessage()))); }
                    });
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lib.pack != null && !busy) showSharing();
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
            @android.annotation.TargetApi(24) // only Android 7+ calls this form; older phones call the one below
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

    /** The screen may only show the app's own server. Anything else would need the internet. */
    private boolean blockOutside(Uri u) {
        PackServer s = lib.server;
        boolean ours = u != null && "http".equals(u.getScheme()) && "127.0.0.1".equals(u.getHost())
            && s != null && u.getPort() == s.port();
        if (!ours) flash(getString(R.string.offline_link));
        return !ours;
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    // ------------------------------------------------------------ sharing

    private void toggleShare() {
        final boolean want = !lib.sharing;
        shareBtn.setEnabled(false);
        if (want && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            try { requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 9); } catch (Exception ignored) {}
        }
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    if (lib.pack == null) return;
                    if (want && PackServer.lanIps().isEmpty()) {
                        ui(new Runnable() {
                            @Override public void run() { showStatus(getString(R.string.no_network)); }
                        });
                        return;
                    }
                    lib.serve(want);
                    if (want) ShareService.start(MainActivity.this);
                    else ShareService.stop(MainActivity.this);
                    ui(new Runnable() {
                        @Override public void run() {
                            showSharing();
                            if (want) web.loadUrl(lib.base() + "#/share");
                        }
                    });
                } catch (final IOException e) {
                    ui(new Runnable() {
                        @Override public void run() { showStatus(String.valueOf(e.getMessage())); }
                    });
                } finally {
                    ui(new Runnable() {
                        @Override public void run() { shareBtn.setEnabled(lib.pack != null); }
                    });
                }
            }
        });
    }

    /** Makes the button and the yellow line say what is true right now. */
    private void showSharing() {
        final boolean on = lib.sharing;
        shareBtn.setText(on ? R.string.share_stop : R.string.share);
        if (!on) { hideStatus(); return; }
        io.execute(new Runnable() {
            @Override public void run() {
                PackServer s = lib.server;
                final List<String> urls = PackServer.lanUrls(s == null ? Library.PORT : s.port());
                ui(new Runnable() {
                    @Override public void run() {
                        if (!lib.sharing) return;
                        StringBuilder sb = new StringBuilder();
                        for (String u : urls) sb.append(sb.length() > 0 ? "   " : "").append(u);
                        showStatus(getString(R.string.sharing_on, sb.toString()) + "\n" + getString(R.string.share_bg));
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------ menu

    private void showMenu() {
        final List<String> labels = new ArrayList<String>();
        final List<Integer> ids = new ArrayList<Integer>();
        labels.add(getString(R.string.get_nearby)); ids.add(5);
        labels.add(getString(R.string.open_pack)); ids.add(1);
        labels.add(getString(R.string.edit_notices)); ids.add(2);
        if (lib.usingImported()) { labels.add(getString(R.string.use_builtin)); ids.add(3); }
        labels.add(getString(R.string.about)); ids.add(4);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int pos, View v, ViewGroup parent) {
                TextView tv = (TextView) super.getView(pos, v, parent);
                tv.setTypeface(font);
                tv.setTextSize(17);
                tv.setMinHeight(dp(56));
                return tv;
            }
        };
        new AlertDialog.Builder(this).setAdapter(ad, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                switch (ids.get(which)) {
                    case 1: pickPack(); break;
                    case 2: editNotices(); break;
                    case 3: useBuiltin(); break;
                    case 4: about(); break;
                    case 5: getNearby(); break;
                    default: break;
                }
            }
        }).show();
    }

    // ---- phone to phone

    private void getNearby() {
        if (busy || lib.pack == null) return;
        busy = true;
        showStatus(getString(R.string.looking));
        io.execute(new Runnable() {
            @Override public void run() {
                final List<String> found = PeerSync.find(PeerSync.candidates(PackServer.lanIps()), Library.PORT, 400);
                ui(new Runnable() {
                    @Override public void run() {
                        if (found.isEmpty()) askAddress();
                        else if (found.size() == 1) lookAt(found.get(0));
                        else choosePhone(found);
                    }
                });
            }
        });
    }

    private void choosePhone(final List<String> found) {
        show(builder(getString(R.string.choose_phone))
            .setItems(found.toArray(new String[0]), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) { lookAt(found.get(which)); }
            })
            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override public void onCancel(DialogInterface d) { done(null); }
            }));
    }

    private void askAddress() {
        hideStatus();
        final EditText box = new EditText(this);
        box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        box.setHint("192.168.43.1:8080");
        box.setTypeface(font);
        show(builder(getString(R.string.get_nearby))
            .setView(pad(text(getString(R.string.none_found)), box))
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { done(null); }
            })
            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override public void onCancel(DialogInterface d) { done(null); }
            })
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String a = box.getText().toString().trim();
                    if (a.isEmpty()) done(null); else lookAt(a);
                }
            }));
    }

    private void lookAt(final String address) {
        showStatus(getString(R.string.looking));
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    final PeerSync.Offer offer = PeerSync.look(address, lib.keys);
                    final PackVerifier.Pack mine = lib.pack;
                    byte[] myManifest = PackVerifier.readSmall(new File(mine.dir, "manifest.json"), "manifest.json not found");
                    if (Arrays.equals(myManifest, offer.manifest)) {
                        done(getString(R.string.same_pack));
                        return;
                    }
                    String myCreated = mine.manifest.optString("created", "");
                    if (!offer.created.isEmpty() && offer.created.compareTo(myCreated) < 0) {
                        ui(new Runnable() {
                            @Override public void run() {
                                show(builder(getString(R.string.get_nearby))
                                    .setView(pad(text(getString(R.string.older_pack, offer.version, mine.version))))
                                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                        @Override public void onClick(DialogInterface d, int w) { done(null); }
                                    })
                                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                        @Override public void onCancel(DialogInterface d) { done(null); }
                                    })
                                    .setPositiveButton(R.string.copy, new DialogInterface.OnClickListener() {
                                        @Override public void onClick(DialogInterface d, int w) { copyFrom(offer); }
                                    }));
                            }
                        });
                        return;
                    }
                    copyFromOnIo(offer);
                } catch (Exception e) {
                    done(getString(R.string.pack_bad, String.valueOf(e.getMessage())));
                }
            }
        });
    }

    private void copyFrom(final PeerSync.Offer offer) {
        io.execute(new Runnable() {
            @Override public void run() { copyFromOnIo(offer); }
        });
    }

    private void copyFromOnIo(PeerSync.Offer offer) {
        try {
            PeerSync.Result r = lib.syncFrom(offer, new PeerSync.Progress() {
                @Override public void step(final int doneCount, final int total, String path) {
                    ui(new Runnable() {
                        @Override public void run() { showStatus(getString(R.string.copying, doneCount, total)); }
                    });
                }
            });
            final String msg = getString(R.string.copied, r.downloaded, r.reused);
            ui(new Runnable() {
                @Override public void run() { web.clearHistory(); web.loadUrl(lib.base()); }
            });
            done(msg);
        } catch (Exception e) {
            try { lib.prepare(); } catch (Exception ignored) {}
            done(getString(R.string.copy_failed, String.valueOf(e.getMessage())));
        }
    }

    /** Ends a long job: shows a last message (or none) and puts the sharing line back. */
    private void done(final String message) {
        ui(new Runnable() {
            @Override public void run() {
                busy = false;
                if (message != null) flash(message);
                else showSharing();
            }
        });
    }

    // ---- pack from a file

    private void pickPack() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*"); // phones disagree about what type a .zip is
        try {
            startActivityForResult(i, REQ_OPEN_PACK);
        } catch (Exception e) {
            flash(String.valueOf(e.getMessage()));
        }
    }

    private void importPack(final Uri uri) {
        busy = true;
        showStatus(getString(R.string.checking));
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    InputStream in = getContentResolver().openInputStream(uri);
                    if (in == null) throw new IOException("cannot open the file");
                    try {
                        lib.importZip(in);
                    } finally {
                        in.close();
                    }
                    ui(new Runnable() {
                        @Override public void run() { web.clearHistory(); web.loadUrl(lib.base()); }
                    });
                    done(getString(R.string.pack_ok));
                } catch (Exception e) {
                    try { lib.prepare(); } catch (Exception ignored) {}
                    done(getString(R.string.pack_bad, String.valueOf(e.getMessage())));
                }
            }
        });
    }

    private void useBuiltin() {
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    lib.useBuiltin();
                    ui(new Runnable() {
                        @Override public void run() { web.clearHistory(); web.loadUrl(lib.base()); }
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
        box.setTypeface(font);
        box.setText(Notices.readText(lib.noticesFile));
        show(builder(getString(R.string.edit_notices))
            .setView(pad(box))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    try {
                        Library.writeText(lib.noticesFile, box.getText().toString());
                        flash(getString(R.string.saved));
                        web.reload();
                    } catch (IOException e) {
                        flash(getString(R.string.save_fail));
                    }
                }
            }));
    }

    private void about() {
        PackVerifier.Pack p = lib.pack;
        String title = p == null ? "?" : (p.titleMy != null && "my".equals(getResources().getConfiguration().locale.getLanguage()) ? p.titleMy : p.title);
        StringBuilder keys = new StringBuilder();
        for (byte[] k : lib.keys) keys.append(keys.length() > 0 ? "\n" : "").append(PackVerifier.fingerprint(k));
        String msg = getString(R.string.about_text, title, p == null ? "?" : p.version,
            lib.appVersion + " (" + getString(R.string.test_build) + ")", keys.toString());
        show(builder(getString(R.string.app_name)).setView(pad(text(msg))).setPositiveButton(R.string.ok, null));
    }

    // ------------------------------------------------------------ saving files

    private void saveFromUrl(Uri u) {
        PackServer s = lib.server;
        File f = s == null || u == null ? null : s.resolve(u.getPath());
        if (f == null) { flash(getString(R.string.save_fail)); return; }
        pendingSave = f;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(PackServer.mime(f.getName()).split(";")[0]);
        i.putExtra(Intent.EXTRA_TITLE, f.getName());
        try {
            startActivityForResult(i, REQ_SAVE_FILE);
        } catch (Exception e) {
            flash(getString(R.string.save_fail));
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
                    final boolean saved = ok;
                    ui(new Runnable() {
                        @Override public void run() { flash(getString(saved ? R.string.saved : R.string.save_fail)); }
                    });
                }
            });
        }
    }

    // ------------------------------------------------------------ helpers

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density); }

    private TextView text(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTypeface(font);
        tv.setTextSize(16);
        tv.setLineSpacing(0, 1.3f);
        tv.setTextIsSelectable(true);
        return tv;
    }

    private View pad(View... views) {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        for (View v : views) box.addView(v);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        return sv;
    }

    private AlertDialog.Builder builder(String title) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTypeface(fontBold);
        t.setTextSize(19);
        t.setPadding(dp(20), dp(18), dp(20), dp(6));
        return new AlertDialog.Builder(this).setCustomTitle(t);
    }

    private void show(AlertDialog.Builder b) {
        if (isFinishing()) return;
        AlertDialog d = b.show();
        for (int which : new int[] {DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE}) {
            Button btn = d.getButton(which);
            if (btn != null) { btn.setTypeface(font); btn.setAllCaps(false); }
        }
    }

    private void ui(Runnable r) { if (!isFinishing()) runOnUiThread(r); }
    private void showStatus(String s) { main.removeCallbacksAndMessages(status); status.setText(s); status.setVisibility(View.VISIBLE); }
    private void hideStatus() { main.removeCallbacksAndMessages(status); status.setVisibility(View.GONE); }

    /** A message that shows for a few seconds in the app's own font, then gives way to the sharing line. */
    private void flash(String s) {
        showStatus(s);
        main.postAtTime(new Runnable() {
            @Override public void run() { showSharing(); }
        }, status, android.os.SystemClock.uptimeMillis() + 7000);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(stopped); } catch (Exception ignored) {}
        main.removeCallbacksAndMessages(null);
        if (isFinishing() && !lib.sharing) {
            // Nobody is being served and the screen is closed: let the server go.
            new Thread(new Runnable() {
                @Override public void run() { if (!lib.sharing) lib.stopServer(); }
            }).start();
        }
        io.shutdown();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
