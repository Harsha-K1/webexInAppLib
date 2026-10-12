package com.webexinapp.lib;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
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

/**
 * Hosts a WebView that can actually use camera and microphone.
 *
 * Two things are required on Android and the stock Volt MX browser widget does neither:
 *  1. Runtime permissions (CAMERA / RECORD_AUDIO) must be requested from an Activity.
 *  2. WebChromeClient.onPermissionRequest must grant the web page's request; the default
 *     implementation denies it, which is why Webex reports "no mic/cam permission" even when
 *     the OS-level permission is on.
 */
public class WebexMeetingActivity extends Activity {

    static final String EXTRA_URL = "url";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_DESKTOP = "desktop";

    private static final int REQ_PERMS = 7001;
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";

    private WebView webView;
    private ProgressBar progress;
    private String url;
    private boolean pageLoaded;
    private PermissionRequest pendingWebRequest;

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        url = getIntent().getStringExtra(EXTRA_URL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        boolean desktop = getIntent().getBooleanExtra(EXTRA_DESKTOP, true);
        if (url == null || url.length() == 0) {
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Top bar
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.parseColor("#1B1B1B"));
        int pad = dp(8);
        bar.setPadding(pad, pad, pad, pad);

        Button close = new Button(this);
        close.setText("Close");
        close.setAllCaps(false);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        bar.addView(close);

        TextView titleView = new TextView(this);
        titleView.setText(title == null ? "" : title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(16);
        titleView.setPadding(dp(12), 0, 0, 0);
        bar.addView(titleView);
        column.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        column.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        progress = new ProgressBar(this);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(progress, plp);

        applyWindowInsets(root);
        setContentView(root);
        configureWebView(desktop);

        if (missingPermissions().isEmpty()) {
            loadMeeting();
        } else {
            requestPermissions(missingPermissions().toArray(new String[0]), REQ_PERMS);
        }
    }

    /**
     * On Android 15+ (and whenever targetSdk >= 35) apps are drawn edge-to-edge, so the
     * status/navigation bars overlap our content. Pad the root by the system bar insets
     * (and the keyboard) so the Close bar and the web page stay clear of them.
     */
    private void applyWindowInsets(final View root) {
        getWindow().setStatusBarColor(Color.parseColor("#1B1B1B"));
        getWindow().setNavigationBarColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int left, top, right, bottom;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.graphics.Insets i = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                                    | WindowInsets.Type.ime());
                    left = i.left; top = i.top; right = i.right; bottom = i.bottom;
                } else {
                    left = insets.getSystemWindowInsetLeft();
                    top = insets.getSystemWindowInsetTop();
                    right = insets.getSystemWindowInsetRight();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(left, top, right, bottom);
                return insets;
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(boolean desktop) {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        if (desktop) {
            s.setUserAgentString(DESKTOP_UA);
        }

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                // Keep everything in the WebView; swallow webex://, intent://, market:// etc.
                // that would otherwise throw the user out to the Webex app or Play Store.
                return scheme != null && !scheme.equals("http") && !scheme.equals("https");
            }

            @Override
            public void onPageFinished(WebView view, String u) {
                pageLoaded = true;
                progress.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override public void run() { handleWebPermission(request); }
                });
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebRequest == request) pendingWebRequest = null;
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (!pageLoaded) {
                    progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                }
            }
        });
    }

    private void loadMeeting() {
        webView.loadUrl(url);
    }

    // ---- permission plumbing -------------------------------------------------------------

    private List<String> missingPermissions() {
        List<String> out = new ArrayList<String>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return out;
        addIfMissing(out, Manifest.permission.CAMERA);
        addIfMissing(out, Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addIfMissing(out, Manifest.permission.BLUETOOTH_CONNECT);
        }
        return out;
    }

    private void addIfMissing(List<String> out, String perm) {
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) out.add(perm);
    }

    private boolean has(String perm) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    /** Maps the page's requested resources onto what the OS has actually granted. */
    private void handleWebPermission(PermissionRequest request) {
        List<String> granted = new ArrayList<String>();
        boolean needsOsPrompt = false;
        for (String res : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)) {
                if (has(Manifest.permission.CAMERA)) granted.add(res); else needsOsPrompt = true;
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) {
                if (has(Manifest.permission.RECORD_AUDIO)) granted.add(res); else needsOsPrompt = true;
            }
            // Other resources (protected media id, MIDI) are denied.
        }
        if (needsOsPrompt && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingWebRequest = request;
            requestPermissions(missingPermissions().toArray(new String[0]), REQ_PERMS);
            return;
        }
        if (granted.isEmpty()) {
            request.deny();
        } else {
            request.grant(granted.toArray(new String[0]));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_PERMS) return;

        boolean permanentlyDenied = false;
        for (int i = 0; i < permissions.length; i++) {
            if (results[i] != PackageManager.PERMISSION_GRANTED
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !shouldShowRequestPermissionRationale(permissions[i])
                    && !Manifest.permission.BLUETOOTH_CONNECT.equals(permissions[i])) {
                permanentlyDenied = true;
            }
        }
        if (permanentlyDenied) {
            Toast.makeText(this, "Camera/microphone blocked. Enable them in app settings.",
                    Toast.LENGTH_LONG).show();
            openAppSettings();
        }

        if (pendingWebRequest != null) {
            PermissionRequest r = pendingWebRequest;
            pendingWebRequest = null;
            handleWebPermission(r);
        }
        if (!pageLoaded && webView.getUrl() == null) {
            loadMeeting();
        }
    }

    private void openAppSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    // ---- lifecycle -----------------------------------------------------------------------

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onResume() { super.onResume(); if (webView != null) webView.onResume(); }
    @Override protected void onPause()  { if (webView != null) webView.onPause(); super.onPause(); }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            // Load blank so the meeting leaves and releases camera/mic.
            webView.loadUrl("about:blank");
            webView.stopLoading();
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
