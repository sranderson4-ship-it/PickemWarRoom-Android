package com.bbbgolf.mobile;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String PREFS = "bbb_golf_mobile";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String MOBILE_VERSION = "0.2.0";
    private static final int FILE_CHOOSER_REQUEST = 5102;

    private SharedPreferences prefs;
    private FrameLayout root;
    private WebView webView;
    private ScrollView setupView;
    private EditText serverUrlInput;
    private TextView connectionMessage;
    private ProgressBar progressBar;
    private String serverUrl = "";
    private ValueCallback<Uri[]> fileChooserCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
        applySystemInsets();
        configureWebView();

        serverUrl = prefs.getString(KEY_SERVER_URL, "");
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            showSetup("Enter the address of the computer running BBB Golf.");
        } else {
            serverUrlInput.setText(serverUrl);
            connectToServer(serverUrl);
        }
    }

    private void configureSystemBars() {
        Window window = getWindow();
        int bg = Color.rgb(13, 15, 14);
        window.setStatusBarColor(bg);
        window.setNavigationBarColor(bg);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                );
            }
        } else {
            window.getDecorView().setSystemUiVisibility(0);
        }
    }

    private void applySystemInsets() {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left;
            int top;
            int right;
            int bottom;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }

            // The web app is laid out only inside the usable Android window.
            // This keeps its fixed header/footer clear of status/navigation bars.
            v.setPadding(left, top, right, bottom);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return WindowInsets.CONSUMED;
            }
            return insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();
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
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        );
        progressParams.gravity = Gravity.TOP;
        root.addView(progressBar, progressParams);

        setupView = new ScrollView(this);
        setupView.setFillViewport(true);
        setupView.setBackgroundColor(Color.rgb(13, 15, 14));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(24), dp(34), dp(24), dp(28));
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
        subtitle.setText("Bingo Bango Bongo Golf");
        subtitle.setTextColor(Color.rgb(0, 184, 137));
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        panel.addView(subtitle, matchWrap(dp(26)));

        TextView help = new TextView(this);
        help.setText(
                "Connect this app to the Windows computer running BBB Golf.\n\n" +
                "Tailscale Serve example:\n" +
                "https://your-machine.tailnet-name.ts.net:8443\n\n" +
                "LAN / Tailscale IP example:\n" +
                "http://192.168.1.25:8787"
        );
        help.setTextColor(Color.rgb(169, 174, 172));
        help.setTextSize(15);
        help.setLineSpacing(0, 1.18f);
        panel.addView(help, matchWrap(dp(18)));

        serverUrlInput = new EditText(this);
        serverUrlInput.setSingleLine(true);
        serverUrlInput.setHint("https://server-address:port");
        serverUrlInput.setTextColor(Color.WHITE);
        serverUrlInput.setHintTextColor(Color.rgb(112, 119, 116));
        serverUrlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        serverUrlInput.setPadding(dp(14), 0, dp(14), 0);
        serverUrlInput.setBackground(rounded(Color.rgb(35, 38, 36), Color.rgb(58, 62, 59), 12));
        panel.addView(serverUrlInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56)
        ));

        Button connect = new Button(this);
        connect.setText("CONNECT TO BBB GOLF");
        connect.setTextColor(Color.WHITE);
        connect.setTextSize(15);
        connect.setTypeface(null, android.graphics.Typeface.BOLD);
        connect.setAllCaps(false);
        connect.setBackground(rounded(Color.rgb(0, 103, 71), Color.TRANSPARENT, 12));
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56)
        );
        connectParams.topMargin = dp(14);
        panel.addView(connect, connectParams);
        connect.setOnClickListener(v -> connectToServer(serverUrlInput.getText().toString()));

        Button clear = new Button(this);
        clear.setText("Clear saved server");
        clear.setTextColor(Color.rgb(200, 205, 202));
        clear.setTextSize(13);
        clear.setAllCaps(false);
        clear.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(48)
        );
        clearParams.topMargin = dp(4);
        panel.addView(clear, clearParams);
        clear.setOnClickListener(v -> {
            prefs.edit().remove(KEY_SERVER_URL).apply();
            serverUrl = "";
            serverUrlInput.setText("");
            webView.loadUrl("about:blank");
            connectionMessage.setText("Saved server cleared.");
        });

        connectionMessage = new TextView(this);
        connectionMessage.setTextColor(Color.rgb(169, 174, 172));
        connectionMessage.setTextSize(13);
        connectionMessage.setGravity(Gravity.CENTER);
        connectionMessage.setPadding(0, dp(10), 0, dp(10));
        panel.addView(connectionMessage, matchWrap(dp(4)));

        TextView version = new TextView(this);
        version.setText("Android wrapper v" + MOBILE_VERSION + "\nPress Android Back from the app home screen to change the server address.");
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
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }
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
                Uri base = safeUri(serverUrl);
                if (base != null && target != null && sameHost(base, target)) {
                    return false;
                }
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
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    String description = error == null ? "Connection failed" : String.valueOf(error.getDescription());
                    showSetup(
                            "Couldn't reach the BBB Golf server: " + description +
                                    "\n\nMake sure the Windows server is running and the Tailscale/LAN address is correct."
                    );
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                showSetup("HTTPS certificate check failed. Use a valid Tailscale HTTPS address or an HTTP LAN/Tailscale IP address.");
            }
        });
    }

    private void connectToServer(String raw) {
        String normalized = normalizeUrl(raw);
        if (normalized == null) {
            connectionMessage.setText("Enter a valid server address.");
            return;
        }

        serverUrl = normalized;
        serverUrlInput.setText(normalized);
        prefs.edit().putString(KEY_SERVER_URL, normalized).apply();
        connectionMessage.setText("Connecting to " + normalized + " …");
        setupView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(normalized);
    }

    private void showSetup(String message) {
        setupView.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
        if (connectionMessage != null) {
            connectionMessage.setText(message == null ? "" : message);
        }
        if (serverUrlInput != null && serverUrl != null && !serverUrl.isEmpty()) {
            serverUrlInput.setText(serverUrl);
        }
    }

    private String normalizeUrl(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;

        if (!v.startsWith("http://") && !v.startsWith("https://")) {
            String lower = v.toLowerCase();
            if (lower.contains(".ts.net")) {
                v = "https://" + v;
            } else {
                v = "http://" + v;
            }
        }

        Uri uri = safeUri(v);
        if (uri == null || uri.getHost() == null || uri.getHost().trim().isEmpty()) {
            return null;
        }
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private Uri safeUri(String value) {
        try {
            return value == null || value.isEmpty() ? null : Uri.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean sameHost(Uri a, Uri b) {
        if (a.getHost() == null || b.getHost() == null) return false;
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
            if (!uris.isEmpty()) {
                result = uris.toArray(new Uri[0]);
            }
        }

        fileChooserCallback.onReceiveValue(result);
        fileChooserCallback = null;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (stroke != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = bottomMargin;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (setupView.getVisibility() == View.VISIBLE) {
            super.onBackPressed();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            showSetup("Change the server address and reconnect, or press Back again to close BBB Golf.");
        }
    }

    @Override
    protected void onDestroy() {
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
