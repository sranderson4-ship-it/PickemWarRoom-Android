package com.pickemwarroom.mobile;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.SslErrorHandler;
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
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PREFS = "warroom_mobile";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String MOBILE_VERSION = "0.3.3-mobile3";

    private SharedPreferences prefs;
    private WebView webView;
    private FrameLayout contentFrame;
    private LinearLayout setupPanel;
    private EditText serverUrlInput;
    private TextView connectionMessage;
    private ProgressBar progressBar;
    private TextView toolbarTitle;
    private String serverUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
        configureWebView();

        serverUrl = prefs.getString(KEY_SERVER_URL, "");
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            showSetup("Enter the address of the computer/server running Pick'em War Room.");
        } else {
            serverUrlInput.setText(serverUrl);
            connectToServer(serverUrl);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(11, 18, 32));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(14), dp(4), dp(8), dp(4));
        toolbar.setBackgroundColor(Color.rgb(15, 23, 42));

        toolbarTitle = new TextView(this);
        toolbarTitle.setText("Pick'em War Room");
        toolbarTitle.setTextColor(Color.WHITE);
        toolbarTitle.setTextSize(17);
        toolbarTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        toolbarTitle.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(toolbarTitle, titleParams);

        Button reload = toolbarButton("↻");
        reload.setContentDescription("Reload War Room");
        reload.setOnClickListener(v -> {
            if (webView.getVisibility() == View.VISIBLE) webView.reload();
        });
        toolbar.addView(reload, new LinearLayout.LayoutParams(dp(44), dp(44)));

        Button settings = toolbarButton("⚙");
        settings.setContentDescription("Server settings");
        settings.setOnClickListener(v -> showSetup("Change the server address, then reconnect."));
        toolbar.addView(settings, new LinearLayout.LayoutParams(dp(44), dp(44)));

        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        contentFrame = new FrameLayout(this);
        root.addView(contentFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(11, 18, 32));
        contentFrame.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setupPanel = new LinearLayout(this);
        setupPanel.setOrientation(LinearLayout.VERTICAL);
        setupPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        setupPanel.setPadding(dp(24), dp(28), dp(24), dp(24));
        setupPanel.setBackgroundColor(Color.rgb(11, 18, 32));

        TextView heading = new TextView(this);
        heading.setText("Connect to War Room");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(26);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        setupPanel.addView(heading, matchWrap(dp(8)));

        TextView help = new TextView(this);
        help.setText("Use your Tailscale Serve URL for access anywhere, or your server's LAN URL while at home.\n\nExamples:\nhttps://warroom.example.ts.net\nhttp://192.168.1.25:8765");
        help.setTextColor(Color.rgb(148, 163, 184));
        help.setTextSize(15);
        help.setLineSpacing(0, 1.15f);
        setupPanel.addView(help, matchWrap(dp(22)));

        serverUrlInput = new EditText(this);
        serverUrlInput.setSingleLine(true);
        serverUrlInput.setHint("https://your-war-room.ts.net");
        serverUrlInput.setTextColor(Color.WHITE);
        serverUrlInput.setHintTextColor(Color.rgb(100, 116, 139));
        serverUrlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        serverUrlInput.setPadding(dp(14), 0, dp(14), 0);
        serverUrlInput.setBackground(rounded(Color.rgb(30, 41, 59), Color.rgb(71, 85, 105), 12));
        setupPanel.addView(serverUrlInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        Button connect = new Button(this);
        connect.setText("CONNECT");
        connect.setTextColor(Color.rgb(11, 18, 32));
        connect.setTextSize(16);
        connect.setTypeface(null, android.graphics.Typeface.BOLD);
        connect.setBackground(rounded(Color.rgb(34, 197, 94), Color.TRANSPARENT, 12));
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        connectParams.topMargin = dp(16);
        setupPanel.addView(connect, connectParams);
        connect.setOnClickListener(v -> connectToServer(serverUrlInput.getText().toString()));

        Button clear = new Button(this);
        clear.setText("Clear saved server");
        clear.setTextColor(Color.WHITE);
        clear.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        clearParams.topMargin = dp(8);
        setupPanel.addView(clear, clearParams);
        clear.setOnClickListener(v -> {
            prefs.edit().remove(KEY_SERVER_URL).apply();
            serverUrl = "";
            serverUrlInput.setText("");
            webView.loadUrl("about:blank");
            connectionMessage.setText("Saved server cleared.");
        });

        connectionMessage = new TextView(this);
        connectionMessage.setTextColor(Color.rgb(148, 163, 184));
        connectionMessage.setTextSize(14);
        connectionMessage.setGravity(Gravity.CENTER);
        connectionMessage.setPadding(0, dp(12), 0, dp(12));
        setupPanel.addView(connectionMessage, matchWrap(0));

        TextView version = new TextView(this);
        version.setText("Android wrapper v" + MOBILE_VERSION + "\nWar Room server version is shown inside the web app.");
        version.setTextColor(Color.rgb(100, 116, 139));
        version.setTextSize(12);
        version.setGravity(Gravity.CENTER);
        setupPanel.addView(version, matchWrap(0));

        contentFrame.addView(setupPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

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
        s.setTextZoom(100);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUserAgentString(s.getUserAgentString() + " PickemWarRoomAndroid/" + MOBILE_VERSION);
        webView.setInitialScale(100);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl();
                Uri base = safeUri(serverUrl);
                if (base != null && target != null && sameHost(base, target)) return false;
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
                    view.evaluateJavascript("(function(){var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}m.content='width=device-width,initial-scale=1,viewport-fit=cover';document.documentElement.style.maxWidth='100%';document.body.style.maxWidth='100%';})();", null);
                    setupPanel.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                    toolbarTitle.setText("Pick'em War Room");
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    String description = error == null ? "Connection failed" : String.valueOf(error.getDescription());
                    showSetup("Couldn't reach War Room: " + description + "\n\nCheck that the server is running and that Tailscale/LAN access is available.");
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                showSetup("Secure connection failed. War Room will not bypass an invalid HTTPS certificate.");
            }
        });
    }

    private void connectToServer(String raw) {
        String normalized = normalizeUrl(raw);
        if (normalized == null) {
            connectionMessage.setText("Enter a valid http:// or https:// server address.");
            return;
        }
        serverUrl = normalized;
        serverUrlInput.setText(normalized);
        prefs.edit().putString(KEY_SERVER_URL, normalized).apply();
        connectionMessage.setText("Connecting to " + normalized + " …");
        webView.setVisibility(View.VISIBLE);
        setupPanel.setVisibility(View.GONE);
        webView.loadUrl(normalized);
    }

    private void showSetup(String message) {
        setupPanel.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
        if (connectionMessage != null) connectionMessage.setText(message == null ? "" : message);
        if (serverUrlInput != null && !serverUrl.isEmpty()) serverUrlInput.setText(serverUrl);
        toolbarTitle.setText("War Room Connection");
    }

    private String normalizeUrl(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;
        if (!v.startsWith("http://") && !v.startsWith("https://")) v = "https://" + v;
        Uri u = safeUri(v);
        if (u == null || u.getHost() == null || u.getHost().trim().isEmpty()) return null;
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    private Uri safeUri(String v) {
        try { return v == null || v.isEmpty() ? null : Uri.parse(v); }
        catch (Exception e) { return null; }
    }

    private boolean sameHost(Uri a, Uri b) {
        if (a.getHost() == null || b.getHost() == null) return false;
        int ap = a.getPort() == -1 ? defaultPort(a.getScheme()) : a.getPort();
        int bp = b.getPort() == -1 ? defaultPort(b.getScheme()) : b.getPort();
        return a.getHost().equalsIgnoreCase(b.getHost()) && ap == bp;
    }

    private int defaultPort(String scheme) { return "https".equalsIgnoreCase(scheme) ? 443 : 80; }

    private void openExternal(Uri uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "No app can open this link.", Toast.LENGTH_SHORT).show(); }
    }

    private Button toolbarButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(20);
        b.setPadding(0, 0, 0, 0);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != Color.TRANSPARENT) g.setStroke(dp(1), stroke);
        return g;
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = bottomMargin;
        return p;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (setupPanel.getVisibility() == View.VISIBLE && serverUrl != null && !serverUrl.isEmpty()) {
            setupPanel.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            toolbarTitle.setText("Pick'em War Room");
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
