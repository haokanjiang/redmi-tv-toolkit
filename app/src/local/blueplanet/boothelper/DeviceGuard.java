package com.redmi.tvtoolkit;

import android.os.Build;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

final class DeviceGuard {
    private DeviceGuard() {}

    static boolean isSupportedDevice() { return DeviceProfile.detect().supported; }

    static String readProperty(String key) throws Exception {
        Process process = new ProcessBuilder("/system/bin/getprop", key)
                .redirectErrorStream(true)
                .start();
        boolean exited = process.waitFor(2, TimeUnit.SECONDS);
        if (!exited) { process.destroyForcibly(); throw new IllegalStateException("getprop timed out"); }
        String value = "";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            if (line != null) {
                value = line.trim();
            }
        } finally {
            process.destroy();
        }
        if (!exited) {
            throw new IllegalStateException("getprop timed out: " + key);
        }
        return value;
    }
}
