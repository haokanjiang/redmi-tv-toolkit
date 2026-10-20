package com.redmi.tvtoolkit;

/** Pure policy shared by recovery adapters and JVM tests. */
final class DeviceCapabilities {
    final String id;
    final boolean homeOverride, bootRecovery, tvManagerBridge, startThirdApp, periodicCleaner, adbRecovery;
    final boolean adbSettingsRecovery;

    private DeviceCapabilities(String id, boolean home, boolean boot, boolean vendor) {
        this.id = id;
        homeOverride = home;
        bootRecovery = boot;
        tvManagerBridge = startThirdApp = periodicCleaner = adbRecovery = vendor;
        adbSettingsRecovery = boot;
    }

    static DeviceCapabilities match(String model, String device, String product, String board) {
        // A conflicting MFTR0 identity must never fall through to private vendor operations.
        if ("MiTV-MFTR0".equalsIgnoreCase(model) || "freeguy".equalsIgnoreCase(device)) {
            return new DeviceCapabilities("MiTV-MFTR0".equalsIgnoreCase(model)
                    && "freeguy".equalsIgnoreCase(device) ? "mftr0" : "unsupported",
                    "MiTV-MFTR0".equalsIgnoreCase(model) && "freeguy".equalsIgnoreCase(device),
                    "MiTV-MFTR0".equalsIgnoreCase(model) && "freeguy".equalsIgnoreCase(device), false);
        }
        if ("blueplanet".equalsIgnoreCase(device) || "blueplanet".equalsIgnoreCase(product)
                || "blueplanet".equalsIgnoreCase(board)) {
            return new DeviceCapabilities("blueplanet", true, true, true);
        }
        return new DeviceCapabilities("unsupported", false, false, false);
    }
}
