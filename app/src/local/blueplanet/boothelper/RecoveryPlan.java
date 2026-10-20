package com.redmi.tvtoolkit;

final class RecoveryPlan {
    interface Actions {
        void homeAccessibility();
        void bootAccessibility();
        void adb();
        void periodicCleaner();
        void startupMediator();
    }

    static void run(DeviceCapabilities caps, boolean recovery, boolean override, Actions actions) {
        if (!caps.bootRecovery || !recovery) return;
        if (caps.adbSettingsRecovery) actions.adb();
        if (caps.homeOverride && override) actions.homeAccessibility();
        if (caps.adbRecovery) actions.bootAccessibility();
        if (caps.periodicCleaner) actions.periodicCleaner();
        if (caps.tvManagerBridge && caps.startThirdApp) actions.startupMediator();
    }
}
