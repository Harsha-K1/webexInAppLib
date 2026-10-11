package com.webexinapp.lib;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;

/**
 * Headless fragment used to request runtime permissions from an Activity we don't own.
 * The result is delivered to the fragment, so the host (Volt MX) Activity needs no changes.
 */
public class PermissionFragment extends Fragment {

    interface Callback { void onResult(String[] permissions, int[] results); }

    private static final String TAG = "WebexPermissionFragment";
    private static final int REQ = 7001;

    private static Callback callback;

    static void request(Activity activity, String[] perms, Callback cb) {
        callback = cb;
        FragmentManager fm = activity.getFragmentManager();
        PermissionFragment f = (PermissionFragment) fm.findFragmentByTag(TAG);
        if (f == null) {
            f = new PermissionFragment();
            fm.beginTransaction().add(f, TAG).commitAllowingStateLoss();
            fm.executePendingTransactions();
        }
        f.requestPermissions(perms, REQ);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        if (code != REQ) return;
        Callback cb = callback;
        callback = null;
        if (cb != null) cb.onResult(permissions, results);
    }
}
