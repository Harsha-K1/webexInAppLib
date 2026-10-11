package com.webexinapp.lib;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.DialogInterface;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen dialog hosting a WebView that can use camera and microphone.
 *
 * The stock Volt MX browser widget has no WebChromeClient.onPermissionRequest, so Webex's
 * getUserMedia() is always denied. Here we (1) request the Android runtime permissions and
 * (2) grant the page's request.
 */
class WebexMeetingDialog extends Dialog {

    private static final String TAG = WebexMeeting.TAG;
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";

    private final Activity activity;
    private final String url;
    private final String title;
    private final boolean desktop;

    private WebView webView;
    private ProgressBar progress;
    private boolean started;
    private PermissionRequest pendingWebRequest;

    WebexMeetingDialog(Activity activity, String url, String title, boolean desktop) {
        super(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.activity = activity;
        this.url = url;
        this.title = title;
        this.desktop = desktop;
    }

    @Override
    public void show() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        buildUi();
        super.show();
        Window w = getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            // Allow the soft keyboard (chat / name field) to resize the content.
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface d, int keyCode, KeyEvent e) {
                if (keyCode == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
                    if (webView != null && webView.canGoBack()) webView.goBack();
                    else dismiss();
                    return true;
                }
                return false;
            }
        });
        setOnDismissListener(new OnDismissListener() {
            @Override public void onDismiss(DialogInterface d) { teardown(); }
        });

        List<String> missing = missingPermissions();
        if (missing.isEmpty()) {
            start();
        } else {
            PermissionFragment.request(activity, missing.toArray(new String[0]),
                    new PermissionFragment.Callback() {
                        @Override public void onResult(String[] p, int[] r) { onPermissionResult(p, r); }
                    });
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bar = new LinearLayout(activity);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.parseColor("#1B1B1B"));
        int pad = dp(8);
        bar.setPadding(pad, pad, pad, pad);

        Button close = new Button(activity);
        close.setText("Close");
        close.setAllCaps(false);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dismiss(); }
        });
        bar.addView(close);

        TextView tv = new TextView(activity);
        tv.setText(title == null ? "" : title);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(16);
        tv.setPadding(dp(12), 0, 0, 0);
        bar.addView(tv);
        column.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(activity);
        column.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        progress = new ProgressBar(activity);
        root.addView(progress, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        setContentView(root);
        configureWebView();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        if (desktop) s.setUserAgentString(DESKTOP_UA);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                // Swallow webex://, intent://, market:// so we never leave the app.
                return scheme != null && !scheme.equals("http") && !scheme.equals("https");
            }

            @Override
            public void onPageFinished(WebView view, String u) {
                progress.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                activity.runOnUiThread(new Runnable() {
                    @Override public void run() { handleWebPermission(request); }
                });
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebRequest == request) pendingWebRequest = null;
            }
        });
    }

    private void start() {
        if (started || webView == null) return;
        started = true;
        webView.loadUrl(url);
    }

    // ---- permissions ---------------------------------------------------------------------

    private List<String> missingPermissions() {
        List<String> out = new ArrayList<String>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return out;
        if (!has(Manifest.permission.CAMERA)) out.add(Manifest.permission.CAMERA);
        if (!has(Manifest.permission.RECORD_AUDIO)) out.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !has(Manifest.permission.BLUETOOTH_CONNECT)) {
            out.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        return out;
    }

    private boolean has(String perm) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || activity.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    private void handleWebPermission(PermissionRequest request) {
        List<String> granted = new ArrayList<String>();
        boolean needsOsPrompt = false;
        for (String res : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)) {
                if (has(Manifest.permission.CAMERA)) granted.add(res); else needsOsPrompt = true;
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) {
                if (has(Manifest.permission.RECORD_AUDIO)) granted.add(res); else needsOsPrompt = true;
            }
        }
        if (needsOsPrompt && pendingWebRequest == null) {
            pendingWebRequest = request;
            PermissionFragment.request(activity, missingPermissions().toArray(new String[0]),
                    new PermissionFragment.Callback() {
                        @Override public void onResult(String[] p, int[] r) { onPermissionResult(p, r); }
                    });
            return;
        }
        Log.i(TAG, "Web permission request " + java.util.Arrays.toString(request.getResources())
                + " -> granted " + granted);
        if (granted.isEmpty()) request.deny();
        else request.grant(granted.toArray(new String[0]));
    }

    private void onPermissionResult(String[] permissions, int[] results) {
        boolean blocked = false;
        for (int i = 0; i < permissions.length; i++) {
            if (results[i] != PackageManager.PERMISSION_GRANTED
                    && !Manifest.permission.BLUETOOTH_CONNECT.equals(permissions[i])
                    && !activity.shouldShowRequestPermissionRationale(permissions[i])) {
                blocked = true;
            }
        }
        if (blocked) {
            Toast.makeText(activity, "Camera/microphone blocked. Enable them in app settings.",
                    Toast.LENGTH_LONG).show();
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", activity.getPackageName(), null));
            activity.startActivity(i);
        }
        if (pendingWebRequest != null) {
            PermissionRequest r = pendingWebRequest;
            pendingWebRequest = null;
            handleWebPermission(r);
        }
        start();
    }

    // ---- cleanup -------------------------------------------------------------------------

    private void teardown() {
        if (webView != null) {
            webView.loadUrl("about:blank"); // leave the meeting, release camera/mic
            webView.stopLoading();
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webView.destroy();
            webView = null;
        }
    }

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
