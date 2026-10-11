package com.webexinapp.lib;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/**
 * Public entry point. Designed to be called from Volt MX through the Java FFI:
 *
 * <pre>
 * var WebexMeeting = java.import("com.webexinapp.lib.WebexMeeting");
 * var KonyMain = java.import("com.konylabs.android.KonyMain");
 * WebexMeeting.open(KonyMain.getActivityContext(), "https://company.webex.com/meet/xyz");
 * </pre>
 */
public final class WebexMeeting {

    private WebexMeeting() {}

    /** Opens the meeting link in an in-app screen with camera/mic permissions handled. */
    public static void open(Context context, String url) {
        open(context, url, "Webex Meeting", true);
    }

    /**
     * @param title        text shown in the top bar
     * @param desktopSite  true to present a desktop-Chrome user agent so Webex serves the
     *                     "join from browser" web client instead of pushing the mobile app.
     */
    public static void open(Context context, String url, String title, boolean desktopSite) {
        Intent intent = new Intent(context, WebexMeetingActivity.class);
        intent.putExtra(WebexMeetingActivity.EXTRA_URL, url);
        intent.putExtra(WebexMeetingActivity.EXTRA_TITLE, title);
        intent.putExtra(WebexMeetingActivity.EXTRA_DESKTOP, desktopSite);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }
}
