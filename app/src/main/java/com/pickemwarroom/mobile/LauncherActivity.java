package com.bbbgolf.mobile;

import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

public class LauncherActivity extends MainActivity {
    private static final String SERVER_URL = "https://larkwebapp.taild46ae8.ts.net:8443";

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        WebView webView = findWebView(getWindow().getDecorView());

        if (webView == null || webView.getVisibility() != View.VISIBLE) {
            moveTaskToBack(true);
            return;
        }

        webView.evaluateJavascript(
                "(function(){if(document.getElementById('modal')){if(window.closeModal)window.closeModal();return 'modal-closed';}return location.hash||'#home';})()",
                result -> handleBackResult(webView, result)
        );
    }

    private void handleBackResult(WebView webView, String result) {
        if (result != null && result.contains("modal-closed")) {
            return;
        }

        String currentUrl = webView.getUrl();
        String fragment = null;
        try {
            if (currentUrl != null) fragment = Uri.parse(currentUrl).getFragment();
        } catch (Exception ignored) {
        }

        boolean home = fragment == null || fragment.isEmpty() || "home".equalsIgnoreCase(fragment);
        if (home) {
            moveTaskToBack(true);
            return;
        }

        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            webView.loadUrl(SERVER_URL + "#home");
        }
    }

    private WebView findWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                WebView found = findWebView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
