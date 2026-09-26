package com.bbbgolf.mobile;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
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
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class LauncherActivity extends Activity {
    private static final String SERVER_URL = "https://larkwebapp.taild46ae8.ts.net:8443";
    private static final String MOBILE_VERSION = "0.3.6";
    private static final int FILE_CHOOSER_REQUEST = 5102;
    private static final String TAILSCALE_PACKAGE = "com.tailscale.ipn";
    private boolean hadNetworkError = false;

    private FrameLayout root;
    private WebView webView;
    private LinearLayout messagePanel;
    private TextView messageText;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> fileChooserCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            configureWindow();
            buildUi();
            configureWebView();
            loadServer();
        } catch (Throwable t) {
            showNativeFatal("BBB Golf could not start.\n\n" + t.getClass().getSimpleName() +
                    (t.getMessage() == null ? "" : ": " + t.getMessage()));
        }
    }

    private void configureWindow() {
        Window w = getWindow();
        int bg = Color.rgb(13, 15, 14);
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        w.getDecorView().setSystemUiVisibility(0);
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
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)
        );
        pp.gravity = Gravity.TOP;
        root.addView(progressBar, pp);

        messagePanel = new LinearLayout(this);
        messagePanel.setOrientation(LinearLayout.VERTICAL);
        messagePanel.setGravity(Gravity.CENTER);
        messagePanel.setPadding(dp(26), dp(26), dp(26), dp(26));
        messagePanel.setBackgroundColor(Color.rgb(13, 15, 14));
        messagePanel.setVisibility(View.GONE);

        TextView title = new TextView(this);
        title.setText("BBB Golf");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        messagePanel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        messageText = new TextView(this);
        messageText.setTextColor(Color.rgb(185, 190, 187));
        messageText.setTextSize(14);
        messageText.setGravity(Gravity.CENTER);
        messageText.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        mp.topMargin = dp(18);
        messagePanel.addView(messageText, mp);

        Button openTailscale = new Button(this);
        openTailscale.setText("Open Tailscale");
        openTailscale.setAllCaps(false);
        openTailscale.setOnClickListener(v -> openTailscale());
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        );
        tp.topMargin = dp(18);
        messagePanel.addView(openTailscale, tp);

        Button retry = new Button(this);
        retry.setText("Retry connection");
        retry.setAllCaps(false);
        retry.setOnClickListener(v -> {
            hadNetworkError = false;
            loadServer();
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        );
        rp.topMargin = dp(10);
        messagePanel.addView(retry, rp);

        root.addView(messagePanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(root);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " BBBGolfAndroid/" + MOBILE_VERSION);

        webView.clearCache(true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params
            ) {
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
                Uri base = Uri.parse(SERVER_URL);
                if (sameServer(base, target)) return false;
                if ("http".equalsIgnoreCase(target.getScheme()) || "https".equalsIgnoreCase(target.getScheme())) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, target));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(LauncherActivity.this, "No browser is available.", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                messagePanel.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    hadNetworkError = true;
                    showMessage("BBB Golf cannot resolve the private Tailscale address.\n\nOpen Tailscale and make sure it shows Connected, then return here and tap Retry connection.\n\nAlso make sure BBBGolfServer v36 is running on the Windows computer.");
                }
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                hadNetworkError = true;
                showMessage("BBB Golf cannot reach the private Tailscale address.\n\nOpen Tailscale and make sure it shows Connected, then return here and tap Retry connection.\n\nNetwork error: " + description);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                showMessage("Secure connection failed. Make sure Tailscale is connected, then tap Retry connection.");
            }
        });
    }

    private void loadServer() {
        if (messagePanel != null) messagePanel.setVisibility(View.GONE);
        if (webView != null) {
            webView.setVisibility(View.VISIBLE);
            webView.loadUrl(SERVER_URL + "/?android=035&t=" + System.currentTimeMillis());
        }
    }

    private void openTailscale() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(TAILSCALE_PACKAGE);
            if (launch != null) {
                startActivity(launch);
                return;
            }
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + TAILSCALE_PACKAGE)));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + TAILSCALE_PACKAGE)));
            } catch (Exception ignored) {
                Toast.makeText(this, "Open Tailscale manually, connect, then return to BBB Golf.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hadNetworkError && webView != null) {
            webView.postDelayed(() -> {
                if (hadNetworkError) loadServer();
            }, 700);
        }
    }

    private void showMessage(String text) {
        if (messageText == null || messagePanel == null || webView == null) return;
        progressBar.setVisibility(View.GONE);
        messageText.setText(text);
        webView.setVisibility(View.GONE);
        messagePanel.setVisibility(View.VISIBLE);
    }

    private void showNativeFatal(String text) {
        try {
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setGravity(Gravity.CENTER);
            panel.setPadding(dp(28), dp(28), dp(28), dp(28));
            panel.setBackgroundColor(Color.rgb(13, 15, 14));

            TextView title = new TextView(this);
            title.setText("BBB Golf");
            title.setTextColor(Color.WHITE);
            title.setTextSize(28);
            title.setGravity(Gravity.CENTER);
            panel.addView(title);

            TextView body = new TextView(this);
            body.setText(text);
            body.setTextColor(Color.rgb(200, 205, 202));
            body.setTextSize(14);
            body.setGravity(Gravity.CENTER);
            body.setPadding(0, dp(18), 0, 0);
            panel.addView(body);

            setContentView(panel);
        } catch (Throwable ignored) {
            finish();
        }
    }

    private boolean sameServer(Uri a, Uri b) {
        if (a == null || b == null || a.getHost() == null || b.getHost() == null) return false;
        int ap = a.getPort() == -1 ? defaultPort(a.getScheme()) : a.getPort();
        int bp = b.getPort() == -1 ? defaultPort(b.getScheme()) : b.getPort();
        return a.getHost().equalsIgnoreCase(b.getHost()) && ap == bp;
    }

    private int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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
        if (messagePanel != null && messagePanel.getVisibility() == View.VISIBLE) {
            moveTaskToBack(true);
            return;
        }
        if (webView == null) {
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
                    } catch (Exception ignored) {}

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
        if (fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = null;
        }
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.destroy();
            } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }
}
