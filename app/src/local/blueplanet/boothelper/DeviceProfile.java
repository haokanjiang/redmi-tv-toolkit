package com.redmi.tvtoolkit;

import android.os.Build;

public final class DeviceProfile {
    private static DeviceProfile cached;
    final DeviceCapabilities capabilities;
    public final String id;
    public final boolean supported, homeOverride, bootRecovery, blueplanet;

    private DeviceProfile(DeviceCapabilities c) {
        capabilities = c;
        id = c.id;
        supported = !"unsupported".equals(id);
        homeOverride = c.homeOverride;
        bootRecovery = c.bootRecovery;
        blueplanet = "blueplanet".equals(id);
    }

    public static synchronized DeviceProfile detect() {
        if (cached != null) return cached;
        String board = "";
        // Never call a vendor provider to identify hardware.
        if (!"freeguy".equalsIgnoreCase(Build.DEVICE)) {
            try { board = DeviceGuard.readProperty("ro.board.platform"); }
            catch (Exception ignored) { }
        }
        cached = new DeviceProfile(DeviceCapabilities.match(Build.MODEL, Build.DEVICE, Build.PRODUCT, board));
        return cached;
    }

    public boolean isSystemHome(String pkg, String cls) {
        return homeOverride && ("com.mitv.tvhome".equals(pkg)
                || ("com.xiaomi.mitv.settings".equals(pkg)
                && ("com.xiaomi.mitv.settings.entry.FallbackHome".equals(cls)
                || ".entry.FallbackHome".equals(cls))));
    }
}
