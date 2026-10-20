package com.redmi.tvtoolkit;
import android.os.Build;
final class DeviceGuard {
    private DeviceGuard() {}
    static DeviceProfile profile() { return DeviceProfile.detect(); }
    static boolean isSupportedDevice() { return profile().supported; }
    static String readProperty(String key) { return ""; }
}
