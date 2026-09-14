package com.bbbgolf.mobile;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;

public class LauncherActivity extends MainActivity {
    private static final String SERVER_URL = "https://larkwebapp.taild46ae8.ts.net:8443";
    private static final String MOBILE_VERSION = "0.3.2";
    private static final int MOBILE_VERSION_CODE = 6;
    private static final int UNKNOWN_SOURCES_REQUEST_V032 = 6103;

    private long updateDownloadIdV032 = -1;
    private Uri pendingInstallUriV032;
    private boolean updateReceiverRegisteredV032 = false;

    private final BroadcastReceiver updateReceiverV032 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id != updateDownloadIdV032) return;

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            Uri uri = dm.getUriForDownloadedFile(id);
            if (uri == null) {
                Toast.makeText(LauncherActivity.this, "Update download failed.", Toast.LENGTH_LONG).show();
                return;
            }
            pendingInstallUriV032 = uri;
            beginInstallV032(uri);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        suppressBaseAutoUpdate();
        patchVersionUi(getWindow().getDecorView());
        registerUpdateReceiverV032();

        getWindow().getDecorView().postDelayed(() -> checkForUpdateV032(false), 2500);
    }

    private void suppressBaseAutoUpdate() {
        try {
            Field field = MainActivity.class.getDeclaredField("updateCheckedThisLaunch");
            field.setAccessible(true);
            field.setBoolean(this, true);
        } catch (Exception ignored) {
        }
    }

    private void patchVersionUi(View view) {
        if (view instanceof Button) {
            Button button = (Button) view;
            CharSequence text = button.getText();
            if (text != null && "Check for app update".contentEquals(text)) {
                button.setOnClickListener(v -> checkForUpdateV032(true));
            }
        } else if (view instanceof TextView) {
            TextView textView = (TextView) view;
            CharSequence text = textView.getText();
            if (text != null && text.toString().startsWith("Android app v")) {
                textView.setText("Android app v" + MOBILE_VERSION + "\nUpdates are checked from the BBB Golf server.");
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                patchVersionUi(group.getChildAt(i));
            }
        }
    }

    private void registerUpdateReceiverV032() {
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(updateReceiverV032, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(updateReceiverV032, filter);
        }
        updateReceiverRegisteredV032 = true;
    }

    private void checkForUpdateV032(boolean userRequested) {
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
                        showUpdateDialogV032(latestName, notes, resolveServerUrlV032(apkUrl));
                    } else if (userRequested) {
                        Toast.makeText(this, "BBB Golf Android v" + MOBILE_VERSION + " is up to date.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                if (userRequested) {
                    runOnUiThread(() -> Toast.makeText(
                            this,
                            "Could not check for updates. Make sure Tailscale and the BBB Golf server are running.",
                            Toast.LENGTH_LONG
                    ).show());
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private String resolveServerUrlV032(String path) {
        if (path == null || path.isEmpty()) return SERVER_URL + "/downloads/BBBGolf-Android.apk";
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        if (!path.startsWith("/")) path = "/" + path;
        return SERVER_URL + path;
    }

    private void showUpdateDialogV032(String versionName, String notes, String apkUrl) {
        new AlertDialog.Builder(this)
                .setTitle("BBB Golf update available" + (versionName.isEmpty() ? "" : " — v" + versionName))
                .setMessage(notes + "\n\nThe update will download privately from your BBB Golf server. Android will then ask you to approve installation.")
                .setNegativeButton("Later", null)
                .setPositiveButton("Update", (dialog, which) -> downloadUpdateV032(apkUrl, versionName))
                .show();
    }

    private void downloadUpdateV032(String apkUrl, String versionName) {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("BBB Golf " + (versionName.isEmpty() ? "update" : "v" + versionName));
            request.setDescription("Downloading app update");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "BBBGolf-update.apk");
            updateDownloadIdV032 = dm.enqueue(request);
            Toast.makeText(this, "Downloading BBB Golf update…", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not start update download: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void beginInstallV032(Uri apkUri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            pendingInstallUriV032 = apkUri;
            try {
                Intent permissionIntent = new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())
                );
                startActivityForResult(permissionIntent, UNKNOWN_SOURCES_REQUEST_V032);
                Toast.makeText(this, "Allow BBB Golf to install updates, then return to the app.", Toast.LENGTH_LONG).show();
                return;
            } catch (Exception e) {
                Toast.makeText(this, "Open Android settings and allow installs from BBB Golf.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        installApkV032(apkUri);
    }

    private void installApkV032(Uri apkUri) {
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UNKNOWN_SOURCES_REQUEST_V032) {
            if (pendingInstallUriV032 != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls())) {
                Uri uri = pendingInstallUriV032;
                pendingInstallUriV032 = null;
                installApkV032(uri);
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

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

    @Override
    protected void onDestroy() {
        if (updateReceiverRegisteredV032) {
            try {
                unregisterReceiver(updateReceiverV032);
            } catch (Exception ignored) {
            }
            updateReceiverRegisteredV032 = false;
        }
        super.onDestroy();
    }
}
