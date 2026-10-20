package com.redmi.tvtoolkit;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

final class RecoveryActions {
    static final String TAG = "RedmiTvToolkit";
    static final String HOME_SERVICE = "com.redmi.tvtoolkit/com.redmi.tvtoolkit.HomeOverrideService";
    static final String BOOT_SERVICE = "com.redmi.tvtoolkit/com.redmi.tvtoolkit.BootAccessibilityService";

    static void run(final Context c) {
        synchronized (HomeOverrideConfig.class) {
            final DeviceProfile profile = DeviceProfile.detect();
            HomeOverrideConfig config = HomeOverrideConfig.load(c);
            if (!profile.bootRecovery || !config.recoveryEnabled) {
                BootJournal.recordPass(c, "disabled", "recovery policy disabled");
                return;
            }
            AdbState diagnostic = null;
            try { if (!profile.blueplanet) diagnostic = AdbRecovery.inspect(c); }
            catch (Exception error) {
                Log.e(TAG, "MFTR0 ADB diagnostic failed", error);
            }
            final AdbState adbBefore = diagnostic;
            if (!profile.blueplanet && adbBefore != null && !adbBefore.needsRepair()
                    && (!config.enabled || bound(c))) {
                BootJournal.recordPass(c, "healthy", adbBefore.toString());
                Log.i(TAG, "recovery already healthy: " + adbBefore);
                return;
            }
            if (!RecoveryScheduler.claim(c)) {
                BootJournal.recordPass(c, "budget_exhausted", "recovery attempt limit reached");
                Log.i(TAG, "recovery ignored: per-boot budget or interval");
                return;
            }
            final StringBuilder failures = new StringBuilder();
            try { RecoveryPlan.run(profile.capabilities, config.recoveryEnabled, config.enabled,
                    new RecoveryPlan.Actions() {
                public void homeAccessibility() {
                    // A listed but unbound/crashed service may need an explicit, bounded rebind.
                    String raw = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                    if (raw != null && !ServiceList.merge(raw, HOME_SERVICE, true).equals(raw) && !bound(c)) {
                        updateService(c, HOME_SERVICE, true);
                        try { Thread.sleep(250); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                    }
                    enableHomeService(c);
                }
                public void bootAccessibility() {
                    updateService(c, BOOT_SERVICE, false);
                    // Retain the original blueplanet Projectivy integration only for that selected launcher.
                    if (HomeOverrideConfig.load(c).target.startsWith("com.spocky.projengmenu/")) {
                        String service = "com.spocky.projengmenu/com.spocky.projengmenu.services.ProjectivyAccessibilityService";
                        try {
                            c.getPackageManager().getServiceInfo(ComponentName.unflattenFromString(service), 0);
                            updateService(c, service, false);
                        } catch (PackageManager.NameNotFoundException absent) {
                            Log.i(TAG, "Projectivy service not installed; skipped");
                        }
                    }
                }
                public void adb() {
                    try {
                        if (profile.blueplanet) LegacyRecoveryActions.ensureNetworkAdb(c);
                        else if (adbBefore != null) AdbRecovery.restoreSetting(c, adbBefore);
                        else failures.append("ADB diagnostic unavailable; ");
                    } catch (RuntimeException error) {
                        failures.append("ADB: ").append(error).append("; ");
                        Log.e(TAG, "ADB recovery failed; continuing Accessibility repair", error);
                    }
                }
                public void periodicCleaner() {
                    try { LegacyRecoveryActions.periodic(c); }
                    catch (RuntimeException error) {
                        failures.append("PeriodicCleaner: ").append(error).append("; ");
                        Log.e(TAG, "blueplanet PeriodicCleaner repair failed", error);
                    }
                }
                public void startupMediator() {
                    try { LegacyRecoveryActions.startup(c); }
                    catch (RuntimeException error) {
                        failures.append("startup mediator: ").append(error).append("; ");
                        Log.e(TAG, "blueplanet startup mediator repair failed", error);
                    }
                }
            });
                String outcome = failures.length() == 0 ? "completed" : "partial_failure";
                String detail = "profile=" + profile.id + " accessibilityBound=" + bound(c)
                        + " target=" + HomeOverrideConfig.load(c).target + " " + failures;
                if (!profile.blueplanet) {
                    try {
                        AdbState after = AdbRecovery.inspect(c);
                        BootJournal.recordAdb(c, "after", after);
                        if (!after.networkReady()) outcome = "network_adbd_unavailable";
                        detail += " " + after;
                    } catch (Exception error) {
                        outcome = "diagnostic_failed";
                        detail += " ADB read-back=" + error;
                        Log.e(TAG, "MFTR0 ADB final diagnostic failed", error);
                    }
                }
                BootJournal.recordPass(c, outcome, detail);
                Log.i(TAG, "recovery pass " + outcome + ": " + detail);
            } catch (RuntimeException error) {
                BootJournal.recordPass(c, "failed", error.toString());
                throw error;
            }
        }
    }

    static void ensureNetworkAdbNow(Context c) {
        synchronized (HomeOverrideConfig.class) {
            DeviceProfile profile = DeviceProfile.detect();
            if (profile.capabilities.adbSettingsRecovery && HomeOverrideConfig.load(c).recoveryEnabled) {
                if (profile.blueplanet) LegacyRecoveryActions.ensureNetworkAdb(c);
                else {
                    try { AdbRecovery.restoreSetting(c, AdbRecovery.inspect(c)); }
                    catch (Exception error) { throw new IllegalStateException("MFTR0 ADB recovery failed", error); }
                }
            }
        }
    }

    static void requirePermission(Context c) {
        if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("WRITE_SECURE_SETTINGS is not granted");
        }
    }

    static void enableHomeService(Context c) { updateService(c, HOME_SERVICE, false); }

    static void updateService(Context c, String service, boolean remove) {
        requirePermission(c);
        String before = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String next = ServiceList.merge(before, service, remove);
        if (!next.equals(before == null ? "" : before)) {
            if (!Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
                    || !next.equals(Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))) {
                throw new IllegalStateException("accessibility list write/read-back failed");
            }
        }
        // Removing one service never disables other services globally.
        int desired = remove ? (next.isEmpty() ? 0 :
                Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0)) : 1;
        if (Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) != desired) {
            if (!Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, desired)
                    || Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, -1) != desired) {
                throw new IllegalStateException("accessibility switch write/read-back failed");
            }
        }
    }

    static boolean bound(Context c) {
        if (!RuntimeStatus.bound) return false;
        AccessibilityManager manager = (AccessibilityManager)c.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager != null) for (AccessibilityServiceInfo info :
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (HOME_SERVICE.equals(ServiceList.normalize(info.getId()))) return true;
        }
        return false;
    }

    static void restore(Context c, String reason) {
        synchronized (HomeOverrideConfig.class) {
            HomeOverrideConfig.disable(c, reason); // persisted before removing services or cancelling jobs
            RecoveryScheduler.cancel(c);
            RuntimeStatus.requestedRevision = -1;
            updateService(c, HOME_SERVICE, true);
            updateService(c, BOOT_SERVICE, true);
            LegacyRecoveryActions.restore(c);
        }
    }

    static void removeHomeService(Context c) { updateService(c, HOME_SERVICE, true); }
}
