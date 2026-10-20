package com.redmi.tvtoolkit;
import android.os.Build;
public final class DeviceProfile {
    public final String id; public final boolean supported, homeOverride, bootRecovery, blueplanet;
    public final String systemHome, fallbackHome;
    private DeviceProfile(String id, boolean supported, boolean homeOverride, boolean bootRecovery, boolean blueplanet, String systemHome, String fallbackHome) {
        this.id=id; this.supported=supported; this.homeOverride=homeOverride; this.bootRecovery=bootRecovery; this.blueplanet=blueplanet; this.systemHome=systemHome; this.fallbackHome=fallbackHome;
    }
    public static DeviceProfile detect() {
        String d=Build.DEVICE==null?"":Build.DEVICE, m=Build.MODEL==null?"":Build.MODEL;
        if ("freeguy".equalsIgnoreCase(d) || "MiTV-MFTR0".equalsIgnoreCase(m)) return new DeviceProfile("xiaomi-mftr0-freeguy", true, true, true, false, "com.mitv.tvhome", "com.xiaomi.mitv.settings/.entry.FallbackHome");
        if ("blueplanet".equalsIgnoreCase(d) || "blueplanet".equalsIgnoreCase(Build.PRODUCT)) return new DeviceProfile("blueplanet", true, false, true, true, "", "");
        return new DeviceProfile("unsupported", false, false, false, false, "", "");
    }
    public boolean isSystemHome(String pkg, String cls) { return homeOverride && ("com.mitv.tvhome".equals(pkg) || ("com.xiaomi.mitv.settings".equals(pkg) && cls != null && cls.endsWith(".entry.FallbackHome"))); }
}
