package com.bbbgolf.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class LauncherActivity extends Activity {
    private static final String SERVER_URL = "https://larkwebapp.taild46ae8.ts.net:8443";
    private static final String MOBILE_VERSION = "0.3.4";
    private static final int MOBILE_VERSION_CODE = 8;
    private static final int FILE_CHOOSER_REQUEST = 5102;
    private static final int UNKNOWN_SOURCES_REQUEST = 5103;
    private static final String PREFS = "bbbgolf_mobile";
    private static final String CACHE_REPAIR_KEY = "cache_repaired_034";

    private FrameLayout root;
    private WebView webView;
    private LinearLayout errorPanel;
    private TextView errorMessage;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> fileChooserCallback;
    private long updateDownloadId = -1;
    private Uri pendingInstallUri;
    private boolean updateCheckedThisLaunch = false;
    private boolean receiverRegistered = false;
    private boolean blankRecoveryAttempted = false;
    private boolean destroyed = false;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id != updateDownloadId) return;

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            Uri uri = dm.getUriForDownloadedFile(id);
            if (uri == null) {
                Toast.makeText(LauncherActivity.this, "Update download failed.", Toast.LENGTH_LONG).show();
                return;
            }
            pendingInstallUri = uri;
            beginInstall(uri);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        buildUi();
        configureWebView();
        registerDownloadReceiver();
        clearOldWebCacheOnce();
        if (!handleDeepLink(getIntent())) loadServer(false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!handleDeepLink(intent)) loadServer(false);
    }

    private void configureWindow() {
        Window window = getWindow();
        int bg = Color.rgb(13, 15, 14);
        window.setStatusBarColor(bg);
        window.setNavigationBarColor(bg);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.getDecorView().setSystemUiVisibility(0);
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(13, 15, 14));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(13, 15, 14));
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        );
        pp.gravity = Gravity.TOP;
        root.addView(progressBar, pp);

        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setPadding(dp(28), dp(28), dp(28), dp(28));
        errorPanel.setBackgroundColor(Color.rgb(13, 15, 14));
        errorPanel.setVisibility(View.GONE);

        TextView title = new TextView(this);
        title.setText("BBB Golf");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        errorPanel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        errorMessage = new TextView(this);
        errorMessage.setTextColor(Color.rgb(180, 185, 182));
        errorMessage.setTextSize(14);
        errorMessage.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        mp.topMargin = dp(18);
        errorPanel.addView(errorMessage, mp);

        Button retry = new Button(this);
        retry.setText("Retry connection");
        retry.setAllCaps(false);
        retry.setOnClickListener(v -> {
            blankRecoveryAttempted = false;
            webView.clearCache(true);
            loadServer(true);
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
        );
        rp.topMargin = dp(18);
        errorPanel.addView(retry, rp);

        root.addView(errorPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(root);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUserAgentString(s.getUserAgentString() + " BBBGolfAndroid/" + MOBILE_VERSION);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookies.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage != null && consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    android.util.Log.e("BBBGolfWebView", consoleMessage.message() + " @ " + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber());
                }
                return super.onConsoleMessage(consoleMessage);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception first) {
                    Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    fallback.addCategory(Intent.CATEGORY_OPENABLE);
                    fallback.setType("image/*");
                    try {
                        startActivityForResult(fallback, FILE_CHOOSER_REQUEST);
                        return true;
                    } catch (ActivityNotFoundException second) {
                        fileChooserCallback = null;
                        Toast.makeText(LauncherActivity.this, "No image picker is available.", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl();
                if (target == null) return false;

                if ("bbbgolf".equalsIgnoreCase(target.getScheme())) {
                    handleBbbGolfUri(target);
                    return true;
                }

                Uri base = Uri.parse(SERVER_URL);
                if (sameHost(base, target)) return false;

                if ("http".equalsIgnoreCase(target.getScheme()) || "https".equalsIgnoreCase(target.getScheme())) {
                    openExternal(target);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (destroyed) return;
                errorPanel.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);

                if (!updateCheckedThisLaunch) {
                    updateCheckedThisLaunch = true;
                    checkForUpdate(false);
                }

                view.postDelayed(() -> verifyWebAppRendered(), 2600);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    showError("Couldn't reach BBB Golf. Make sure Tailscale is connected and the Windows server is running.");
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                showError("Secure connection failed. Make sure Tailscale is connected, then retry.");
            }
        });
    }

    private void clearOldWebCacheOnce() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean(CACHE_REPAIR_KEY, false)) {
            webView.clearCache(true);
            prefs.edit().putBoolean(CACHE_REPAIR_KEY, true).apply();
        }
    }

    private void loadServer(boolean hardRefresh) {
        errorPanel.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        String suffix = hardRefresh
                ? "/?mobileApp=" + MOBILE_VERSION_CODE + "&refresh=" + System.currentTimeMillis()
                : "/?mobileApp=" + MOBILE_VERSION_CODE;
        webView.loadUrl(SERVER_URL + suffix);
    }

    private void verifyWebAppRendered() {
        if (destroyed || webView.getVisibility() != View.VISIBLE) return;
        String script = "(function(){var a=document.getElementById('app');var f=document.getElementById('bootFallback');" +
                "return JSON.stringify({ok:!!(a&&a.textContent&&a.textContent.trim().length>0),fallback:!!f,text:a?(a.textContent||'').trim().slice(0,80):''});})()";
        webView.evaluateJavascript(script, result -> {
            if (destroyed) return;
            boolean bad = result == null || "null".equals(result) || result.contains("\\\"fallback\\\":true") || result.contains("\\\"ok\\\":false");
            if (!bad) return;

            if (!blankRecoveryAttempted) {
                blankRecoveryAttempted = true;
                repairBlankWebView();
            } else {
                showError("BBB Golf reached the server, but the web interface did not render. Tap Retry connection to reload the app files.");
            }
        });
    }

    private void repairBlankWebView() {
        webView.clearCache(true);
        String script = "(async function(){try{if('serviceWorker'in navigator){var r=await navigator.serviceWorker.getRegistrations();await Promise.all(r.map(x=>x.unregister()));}" +
                "if(window.caches){var k=await caches.keys();await Promise.all(k.map(x=>caches.delete(x)));}}catch(e){}return true;})()";
        webView.evaluateJavascript(script, ignored -> webView.postDelayed(() -> loadServer(true), 400));
    }

    private void showError(String message) {
        errorMessage.setText(message);
        progressBar.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        errorPanel.setVisibility(View.VISIBLE);
    }

    private boolean handleDeepLink(Intent intent) {
        if (intent == null || intent.getData() == null) return false;
        Uri uri = intent.getData();
        if (!"bbbgolf".equalsIgnoreCase(uri.getScheme())) return false;
        handleBbbGolfUri(uri);
        return true;
    }

    private void handleBbbGolfUri(Uri uri) {
        String host = uri.getHost() == null ? "" : uri.getHost();
        if ("sso".equalsIgnoreCase(host)) {
            String provider = uri.getQueryParameter("provider");
            if (!"google".equals(provider) && !"apple".equals(provider)) return;
            openExternal(Uri.parse(SERVER_URL + "/auth/" + provider + "/start?mobile=1"));
            return;
        }

        if ("auth".equalsIgnoreCase(host)) {
            String code = uri.getQueryParameter("code");
            if (code == null || code.isEmpty()) return;
            errorPanel.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            webView.loadUrl(SERVER_URL + "/auth/handoff?code=" + Uri.encode(code));
        }
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No browser is available.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean sameHost(Uri a, Uri b) {
        if (a == null || b == null || a.getHost() == null || b.getHost() == null) return false;
        int ap = a.getPort() == -1 ? defaultPort(a.getScheme()) : a.getPort();
        int bp = b.getPort() == -1 ? defaultPort(b.getScheme()) : b.getPort();
        return a.getHost().equalsIgnoreCase(b.getHost()) && ap == bp;
    }

    private int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private void checkForUpdate(boolean userRequested) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(SERVER_URL + "/api/mobile-update?versionCode=" + MOBILE_VERSION_CODE);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/json");
                int response = connection.getResponseCode();
                if (response != 200) throw new Exception("HTTP " + response);

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                reader.close();

                JSONObject json = new JSONObject(body.toString());
                int latestCode = json.optInt("versionCode", 0);
                String latestName = json.optString("versionName", "");
                String apkUrl = json.optString("apkUrl", "/downloads/BBBGolf-Android.apk");
                String notes = json.optString("notes", "A newer BBB Golf Android app is available.");

                runOnUiThread(() -> {
                    if (latestCode > MOBILE_VERSION_CODE) {
                        showUpdateDialog(latestName, notes, resolveServerUrl(apkUrl));
                    } else if (userRequested) {
                        Toast.makeText(this, "BBB Golf Android v" + MOBILE_VERSION + " is up to date.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                if (userRequested) {
                    runOnUiThread(() -> Toast.makeText(this, "Could not check for updates.", Toast.LENGTH_LONG).show());
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private String resolveServerUrl(String path) {
        if (path == null || path.isEmpty()) return SERVER_URL + "/downloads/BBBGolf-Android.apk";
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        if (!path.startsWith("/")) path = "/" + path;
        return SERVER_URL + path;
    }

    private void showUpdateDialog(String versionName, String notes, String apkUrl) {
        new AlertDialog.Builder(this)
                .setTitle("BBB Golf update available" + (versionName.isEmpty() ? "" : " — v" + versionName))
                .setMessage(notes + "\n\nThe update will download privately from your BBB Golf server.")
                .setNegativeButton("Later", null)
                .setPositiveButton("Update", (dialog, which) -> downloadUpdate(apkUrl, versionName))
                .show();
    }

    private void downloadUpdate(String apkUrl, String versionName) {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("BBB Golf " + (versionName.isEmpty() ? "update" : "v" + versionName));
            request.setDescription("Downloading app update");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "BBBGolf-update.apk");
            updateDownloadId = dm.enqueue(request);
            Toast.makeText(this, "Downloading BBB Golf update…", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not start update download.", Toast.LENGTH_LONG).show();
        }
    }

    private void registerDownloadReceiver() {
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void beginInstall(Uri apkUri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            pendingInstallUri = apkUri;
            try {
                Intent permissionIntent = new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())
                );
                startActivityForResult(permissionIntent, UNKNOWN_SOURCES_REQUEST);
                Toast.makeText(this, "Allow BBB Golf to install updates, then return.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Allow installs from BBB Golf in Android settings.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        installApk(apkUri);
    }

    private void installApk(Uri apkUri) {
        if (apkUri == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open Android installer.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == UNKNOWN_SOURCES_REQUEST) {
            if (pendingInstallUri != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls())) {
                Uri uri = pendingInstallUri;
                pendingInstallUri = null;
                installApk(uri);
            }
            return;
        }

        if (requestCode != FILE_CHOOSER_REQUEST || fileChooserCallback == null) return;

        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri uri = clip.getItemAt(i).getUri();
                    if (uri != null) uris.add(uri);
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) result = uris.toArray(new Uri[0]);
        }

        fileChooserCallback.onReceiveValue(result);
        fileChooserCallback = null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (errorPanel.getVisibility() == View.VISIBLE) {
            moveTaskToBack(true);
            return;
        }

        webView.evaluateJavascript(
                "(function(){var m=document.getElementById('modal');if(m){if(window.closeModal)window.closeModal();return 'modal';}return location.hash||'#home';})()",
                result -> {
                    if (result != null && result.contains("modal")) return;

                    String currentUrl = webView.getUrl();
                    String fragment = null;
                    try {
                        if (currentUrl != null) fragment = Uri.parse(currentUrl).getFragment();
                    } catch (Exception ignored) {
                    }

                    boolean home = fragment == null || fragment.isEmpty() || "home".equalsIgnoreCase(fragment);
                    if (home) {
                        moveTaskToBack(true);
                    } else if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        webView.loadUrl(SERVER_URL + "/#home");
                    }
                }
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (receiverRegistered) {
            try {
                unregisterReceiver(downloadReceiver);
            } catch (Exception ignored) {
            }
            receiverRegistered = false;
        }
        if (fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
