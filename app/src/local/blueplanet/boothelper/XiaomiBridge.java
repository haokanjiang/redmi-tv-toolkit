package com.redmi.tvtoolkit;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * Minimal wrapper around the verified Xiaomi TVManager provider behavior.
 * No caller can choose a method, property key, service name or setting name.
 */
final class XiaomiBridge {
    private static final String TAG = "RedmiTvToolkit";
    private static final Uri PROVIDER =
            Uri.parse("content://com.mitv.security.permission");
    private static final String TVMANAGER_PACKAGE = "com.xiaomi.mitv.tvmanager";

    private XiaomiBridge() {}

    static void setStartupMediator(Context context) {
        if (!DeviceProfile.detect().blueplanet) {
            return;
        }
        Bundle extras = new Bundle();
        extras.putString("start_3rd_app", context.getPackageName());
        call(context, "start3rdApp", extras);
    }

    static void restoreStartupSetting(Context context, String packageName) {
        if (!DeviceProfile.detect().blueplanet) {
            return;
        }
        String safeValue = packageName == null ? "" : packageName.trim();
        if (!safeValue.isEmpty()
                && !safeValue.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) {
            throw new IllegalArgumentException("invalid package name");
        }
        Bundle extras = new Bundle();
        extras.putString("database", "system");
        extras.putString("name", "start_3rd_app");
        extras.putString("value", safeValue);
        call(context, "putSetting", extras);
    }

    static void restartAdbd(Context context) {
        if (!DeviceProfile.detect().blueplanet) {
            return;
        }
        Bundle extras = new Bundle();
        extras.putString("key", "ctl.restart");
        extras.putString("value", "adbd");
        call(context, "setProperty", extras);
    }

    private static Bundle call(Context context, String method, Bundle extras) {
        try {
            return context.getContentResolver().call(
                    PROVIDER, method, TVMANAGER_PACKAGE, extras);
        } catch (Throwable error) {
            Log.e(TAG, "Xiaomi bridge call failed: " + method, error);
            return null;
        }
    }
}



