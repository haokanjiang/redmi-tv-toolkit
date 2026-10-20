package com.redmi.tvtoolkit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Protected by android.permission.DUMP in the manifest, enforced by ActivityManager before delivery.
 * Binder.getCallingUid() inside onReceive is NOT the original sender and must not be used for auth.
 * Explicit ordered ADB broadcast response: result=1/2, data=TOOLKIT1:<base64 UTF-8 JSON>.
 */
public final class ToolkitControlReceiver extends BroadcastReceiver {
    public static final String PREFIX = "com.redmi.tvtoolkit.action.";
    @Override public void onReceive(Context c, Intent intent) {
        if (!isOrderedBroadcast()) return; // caller must be able to receive an explicit result
        try {
            String action = intent == null ? "" : intent.getAction();
            synchronized (HomeOverrideConfig.class) {
                if ((PREFIX + "HOME_SET").equals(action)) {
                    RecoveryActions.requirePermission(c);
                    Map<String, String> previous = HomeOverrideConfig.snapshot(c);
                    String before = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                    boolean wasListed = !ServiceList.merge(before, RecoveryActions.HOME_SERVICE, true)
                            .equals(before == null ? "" : before);
                    int previousSwitch = Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
                    try {
                        HomeOverrideConfig.set(c, intent.getStringExtra("target"), intent.getStringExtra("fallback"));
                        RecoveryActions.enableHomeService(c);
                        RecoveryScheduler.ensure(c, true);
                        RuntimeStatus.requestedRevision = HomeOverrideConfig.load(c).revision;
                        HomeOverrideService.configurationChanged();
                    } catch (RuntimeException error) {
                        HomeOverrideConfig.rollback(c, previous);
                        if (!wasListed) RecoveryActions.removeHomeService(c);
                        String after = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                        if (previousSwitch == 0 && (before == null ? "" : before).equals(after == null ? "" : after)) {
                            if (!Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0)
                                    || Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, -1) != 0) {
                                throw new IllegalStateException("accessibility switch rollback failed", error);
                            }
                        }
                        RecoveryScheduler.ensure(c, false);
                        throw error;
                    }
                } else if ((PREFIX + "HOME_RESTORE").equals(action)) {
                    RecoveryActions.restore(c, "restored");
                    HomeOverrideService.configurationChanged();
                } else if (!(PREFIX + "HOME_STATUS").equals(action)) {
                    throw new IllegalArgumentException("unsupported action");
                }
                respond(c, true, "configuration_verified");
            }
        } catch (Exception error) {
            Log.e(RecoveryActions.TAG, "control failed", error);
            respond(c, false, error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void respond(Context c, boolean ok, String message) {
        try {
            HomeOverrideConfig config = HomeOverrideConfig.load(c);
            JSONObject json = new JSONObject();
            json.put("protocol", 1).put("ok", ok).put("message", message)
                    .put("enabled", config.enabled).put("recovery", config.recoveryEnabled)
                    .put("target", config.target).put("fallback", config.fallback).put("lastGood", config.lastGood)
                    .put("revision", config.revision).put("phase", config.phase)
                    .put("bound", RuntimeStatus.bound && RecoveryActions.bound(c))
                    .put("foreground", RuntimeStatus.foreground).put("outcome", RuntimeStatus.outcome);
            BootJournal.addStatus(c, json);
            setResultCode(ok ? 1 : 2);
            setResultData("TOOLKIT1:" + Base64.encodeToString(json.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        } catch (Exception error) {
            setResultCode(2);
            setResultData("TOOLKIT_ERROR");
        }
    }
}
