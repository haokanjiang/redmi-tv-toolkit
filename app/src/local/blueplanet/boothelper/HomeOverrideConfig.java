package com.redmi.tvtoolkit;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.HashMap;
import java.util.Map;

public final class HomeOverrideConfig {
    private static final String PREF = "home_override";
    final boolean enabled, recoveryEnabled;
    final String target, fallback, lastGood, phase;
    final int revision;

    private HomeOverrideConfig(Map<String, String> values, boolean legacy) {
        enabled = Boolean.parseBoolean(values.get("enabled"));
        recoveryEnabled = Boolean.parseBoolean(values.containsKey("recovery") ? values.get("recovery") : "" + legacy);
        target = value(values, "target", legacy ? "com.spocky.projengmenu/.ui.home.MainActivity" : "");
        fallback = value(values, "fallback", "");
        lastGood = value(values, "lastGood", "");
        phase = value(values, "phase", "idle");
        revision = Integer.parseInt(value(values, "revision", "0"));
    }

    private static String value(Map<String, String> v, String key, String defaultValue) {
        return v.containsKey(key) ? v.get(key) : defaultValue;
    }

    static Context storage(Context c) {
        return Build.VERSION.SDK_INT >= 24 ? c.createDeviceProtectedStorageContext() : c;
    }

    static VerifiedStore.Backend backend(Context c) {
        final SharedPreferences prefs = storage(c).getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return new VerifiedStore.Backend() {
            public Map<String, String> read() {
                Map<String, String> values = new HashMap<>();
                for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                    values.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
                return values;
            }
            public boolean write(Map<String, String> values) {
                SharedPreferences.Editor edit = prefs.edit().clear();
                for (Map.Entry<String, String> entry : values.entrySet()) edit.putString(entry.getKey(), entry.getValue());
                return edit.commit();
            }
        };
    }

    static synchronized HomeOverrideConfig load(Context c) {
        return new HomeOverrideConfig(backend(c).read(), DeviceProfile.detect().blueplanet);
    }

    static synchronized Map<String, String> snapshot(Context c) { return new HashMap<>(backend(c).read()); }
    static synchronized void rollback(Context c, Map<String, String> old) { VerifiedStore.replace(backend(c), old); }

    static synchronized void set(Context c, String target, String fallback) {
        DeviceProfile profile = DeviceProfile.detect();
        if (!profile.homeOverride) throw new IllegalStateException("unsupported device");
        String selected = LauncherTarget.resolve(c, target).component.flattenToString();
        HomeOverrideConfig old = load(c);
        String previous = fallback == null ? old.lastGood : fallback;
        if (!previous.isEmpty()) {
            if (fallback != null) previous = LauncherTarget.resolve(c, previous).component.flattenToString();
            else {
                try { previous = LauncherTarget.resolve(c, previous).component.flattenToString(); }
                catch (IllegalArgumentException unavailable) { previous = ""; }
            }
        }
        if (selected.equals(previous)) previous = "";
        VerifiedStore.replace(backend(c), ConfigValues.select(snapshot(c), selected, previous));
    }

    static synchronized void observed(Context c, String component, boolean fallback) {
        VerifiedStore.replace(backend(c), ConfigValues.confirm(snapshot(c), component, fallback));
    }

    static synchronized void restore(Context c) { disable(c, "restored"); }
    static synchronized void disable(Context c, String reason) {
        VerifiedStore.replace(backend(c), ConfigValues.disable(snapshot(c), reason));
    }
}
