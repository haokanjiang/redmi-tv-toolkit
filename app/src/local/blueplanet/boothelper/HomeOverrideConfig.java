package com.redmi.tvtoolkit;
import android.content.*;
import android.content.pm.PackageManager;
import android.text.TextUtils;
public final class HomeOverrideConfig {
    private static final String PREF="home_override";
    public final boolean enabled, recoveryEnabled; public final String target, fallback;
    private HomeOverrideConfig(boolean e, boolean r, String t, String f) { enabled=e; recoveryEnabled=r; target=t; fallback=f; }
    static HomeOverrideConfig load(Context c) { SharedPreferences p=c.createDeviceProtectedStorageContext().getSharedPreferences(PREF, Context.MODE_PRIVATE); return new HomeOverrideConfig(p.getBoolean("enabled",false),p.getBoolean("recovery",false),p.getString("target",""),p.getString("fallback","")); }
    static boolean validComponent(Context c, String component) { if (TextUtils.isEmpty(component)||component.indexOf('/')<1) return false; try { c.getPackageManager().getActivityInfo(ComponentName.unflattenFromString(component),0); return true; } catch(Throwable e){ return false; } }
    static boolean validTarget(Context c, String component) { if (!validComponent(c, component)) return false; String pkg=component.substring(0,component.indexOf('/')); return !(pkg.equals("android")||pkg.equals("com.mitv.tvhome")||pkg.equals("com.xiaomi.mitv.settings")||pkg.equals("com.redmi.tvtoolkit")); }
    static boolean set(Context c, String target, String fallback) { if (!validTarget(c,target)) return false; if (!validComponent(c,fallback)) fallback=""; c.createDeviceProtectedStorageContext().getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean("enabled",true).putBoolean("recovery",true).putString("target",target).putString("fallback",fallback).apply(); return true; }
    static void restore(Context c) { c.createDeviceProtectedStorageContext().getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean("enabled",false).putBoolean("recovery",false).apply(); }
    static void disableOnFailure(Context c) { restore(c); }
}
