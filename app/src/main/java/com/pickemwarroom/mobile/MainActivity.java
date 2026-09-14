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
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String SERVER_URL = "https://larkwebapp.taild46ae8.ts.net:8443";
    private static final String MOBILE_VERSION = "0.3.1";
    private static final int MOBILE_VERSION_CODE = 5;
    private static final int FILE_CHOOSER_REQUEST = 5102;
    private static final int UNKNOWN_SOURCES_REQUEST = 5103;

    private FrameLayout root;
    private WebView webView;
    private ScrollView setupView;
    private TextView connectionMessage;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> fileChooserCallback;
    private long updateDownloadId = -1;
    private Uri pendingInstallUri;
    private boolean updateCheckedThisLaunch = false;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id != updateDownloadId) return;

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            Uri uri = dm.getUriForDownloadedFile(id);
            if (uri == null) {
                Toast.makeText(MainActivity.this, "Update download failed.", Toast.LENGTH_LONG).show();
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
        connectToPrivateServer();
    }

    private void configureWindow() {
        Window window = getWindow();
        int bg = Color.rgb(13, 15, 14);
        window.setStatusBarColor(bg);
        window.setNavigationBarColor(bg);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.getDecorView().setSystemUiVisibility(0);
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

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(13, 15, 14));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(13, 15, 14));
        webView.setVisibility(View.GONE);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)
        );
        progressParams.gravity = Gravity.TOP;
        root.addView(progressBar, progressParams);

        setupView = new ScrollView(this);
        setupView.setFillViewport(true);
        setupView.setBackgroundColor(Color.rgb(13, 15, 14));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(24), dp(44), dp(24), dp(28));
        setupView.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView heading = new TextView(this);
        heading.setText("BBB Golf");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(30);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        panel.addView(heading, matchWrap(dp(6)));

        TextView subtitle = new TextView(this);
        subtitle.setText("Private Tailscale App");
        subtitle.setTextColor(Color.rgb(0, 184, 137));
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);
        panel.addView(subtitle, matchWrap(dp(24)));

        connectionMessage = new TextView(this);
        connectionMessage.setTextColor(Color.rgb(180, 185, 182));
        connectionMessage.setTextSize(14);
        connectionMessage.setGravity(Gravity.CENTER);
        connectionMessage.setLineSpacing(0, 1.15f);
        connectionMessage.setText("Connecting to your private BBB Golf server…");
        panel.addView(connectionMessage, matchWrap(dp(20)));

        Button retry = new Button(this);
        retry.setText("Retry connection");
        retry.setTextColor(Color.WHITE);
        retry.setTextSize(15);
        retry.setTypeface(null, android.graphics.Typeface.BOLD);
        retry.setAllCaps(false);
        retry.setBackground(rounded(Color.rgb(0, 103, 71), Color.TRANSPARENT, 12));
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
        );
        panel.addView(retry, retryParams);
        retry.setOnClickListener(v -> connectToPrivateServer());

        Button checkUpdate = new Button(this);
        checkUpdate.setText("Check for app update");
        checkUpdate.setTextColor(Color.WHITE);
        checkUpdate.setTextSize(14);
        checkUpdate.setAllCaps(false);
        checkUpdate.setBackground(rounded(Color.rgb(35, 38, 36), Color.rgb(58, 62, 59), 12));
        LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        );
        updateParams.topMargin = dp(10);
        panel.addView(checkUpdate, updateParams);
        checkUpdate.setOnClickListener(v -> checkForUpdate(true));

        TextView serverInfo = new TextView(this);
        serverInfo.setText("Server is built into this app.\n" + SERVER_URL + "\n\nMake sure Tailscale is connected on this phone.");
        serverInfo.setTextColor(Color.rgb(112, 119, 116));
        serverInfo.setTextSize(11);
        serverInfo.setGravity(Gravity.CENTER);
        serverInfo.setPadding(0, dp(20), 0, dp(8));
        panel.addView(serverInfo, matchWrap(dp(4)));

        TextView version = new TextView(this);
        version.setText("Android app v" + MOBILE_VERSION + "\nUpdates are checked from the BBB Golf server.");
        version.setTextColor(Color.rgb(112, 119, 116));
        version.setTextSize(11);
        version.setGravity(Gravity.CENTER);
        panel.addView(version, matchWrap(0));

        root.addView(setupView, new FrameLayout.LayoutParams(
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
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUserAgentString(s.getUserAgentString() + " BBBGolfAndroid/" + MOBILE_VERSION);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = filePathCallback;
                Intent intent;
                try {
                    intent = fileChooserParams.createIntent();
                } catch (Exception e) {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                }
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException e) {
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, "No file picker is available.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl();
                Uri base = Uri.parse(SERVER_URL);
                if (target != null && sameHost(base, target)) return false;
                if (target != null && ("http".equals(target.getScheme()) || "https".equals(target.getScheme()))) {
                    openExternal(target);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && !"about:blank".equals(url)) {
                    setupView.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                    if (!updateCheckedThisLaunch) {
                        updateCheckedThisLaunch = true;
                        checkForUpdate(false);
                    }
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    String description = error == null ? "Connection failed" : String.valueOf(error.getDescription());
                    showConnectionScreen("Couldn't reach the private BBB Golf server: " + description + "\n\nMake sure Tailscale is connected and the Windows BBB Golf server is running.");
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                showConnectionScreen("Secure connection failed. Make sure Tailscale is connected, then tap Retry connection.");
            }
        });
    }

    private void connectToPrivateServer() {
        connectionMessage.setText("Connecting to your private BBB Golf server…");
        setupView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(SERVER_URL);
    }

    private void showConnectionScreen(String message) {
        setupView.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
        connectionMessage.setText(message == null ? "" : message);
    }

    private void checkForUpdate(boolean userRequested) {
        if (userRequested) connectionMessage.setText("Checking for app update…");

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
                        connectionMessage.setText("BBB Golf Android v" + MOBILE_VERSION + " is up to date.");
                    }
                });
            } catch (Exception e) {
                if (userRequested) {
                    runOnUiThread(() -> connectionMessage.setText("Could not check for updates. Make sure Tailscale and the BBB Golf server are running."));
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
                .setMessage(notes + "\n\nThe update will download privately from your BBB Golf server. Android will then ask you to approve installation.")
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
            Toast.makeText(this, "Could not start update download: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
                Toast.makeText(this, "Allow BBB Golf to install updates, then return to the app.", Toast.LENGTH_LONG).show();
                return;
            } catch (Exception e) {
                Toast.makeText(this, "Open Android settings and allow installs from BBB Golf.", Toast.LENGTH_LONG).show();
                return;
            }
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
            Toast.makeText(this, "Could not open Android installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
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

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app can open this link.", Toast.LENGTH_SHORT).show();
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
        if (resultCode == RESULT_OK) {
            List<Uri> uris = new ArrayList<>();
            if (data != null) {
                ClipData clip = data.getClipData();
                if (clip != null) {
                    for (int i = 0; i < clip.getItemCount(); i++) {
                        Uri uri = clip.getItemAt(i).getUri();
                        if (uri != null) uris.add(uri);
                    }
                } else if (data.getData() != null) {
                    uris.add(data.getData());
                }
            }
            if (!uris.isEmpty()) result = uris.toArray(new Uri[0]);
        }

        fileChooserCallback.onReceiveValue(result);
        fileChooserCallback = null;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != Color.TRANSPARENT) g.setStroke(dp(1), stroke);
        return g;
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        p.bottomMargin = bottomMargin;
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (setupView.getVisibility() == View.VISIBLE) {
            connectToPrivateServer();
            return;
        }
        if (webView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        if (webView.getVisibility() == View.VISIBLE) {
            webView.loadUrl(SERVER_URL);
        }
    }

    @Override
    protected void onDestroy() {
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
