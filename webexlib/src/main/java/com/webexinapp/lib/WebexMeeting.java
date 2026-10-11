package com.webexinapp.lib;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;

/**
 * Public entry point, designed for the Volt MX Java FFI. Needs no manifest activity: the
 * meeting is shown in a full-screen dialog on top of the Activity you pass in.
 *
 * <pre>
 * var WebexMeeting = java.import("com.webexinapp.lib.WebexMeeting");
 * var KonyMain = java.import("com.konylabs.android.KonyMain");
 * var ok = WebexMeeting.open(KonyMain.getActivityContext(), url);
 * if (!ok) { kony.application.openURL(url); }   // optional fallback
 * </pre>
 *
 * Filter Logcat by tag "WebexMeeting" to see what happened.
 */
public final class WebexMeeting {

    static final String TAG = "WebexMeeting";

    private WebexMeeting() {}

    public static boolean open(Context context, String url) {
        return open(context, url, "Webex Meeting", true);
    }

    /**
     * @param desktopSite true to use a desktop-Chrome user agent so Webex serves the
     *                    "join from browser" client rather than pushing the mobile app.
     * @return true if the in-app meeting screen was scheduled, false if it could not be
     *         shown (no Activity context, empty URL).
     */
    public static boolean open(Context context, final String url, final String title,
                               final boolean desktopSite) {
        final Activity activity = findActivity(context);
        if (activity == null) {
            Log.e(TAG, "open() needs an Activity context, got: " + context);
            return false;
        }
        if (url == null || url.length() == 0) {
            Log.e(TAG, "open() called with empty url");
            return false;
        }
        Log.i(TAG, "Opening in-app: " + url);
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                try {
                    new WebexMeetingDialog(activity, url, title, desktopSite).show();
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to show meeting dialog", t);
                }
            }
        });
        return true;
    }

    private static Activity findActivity(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }
}
