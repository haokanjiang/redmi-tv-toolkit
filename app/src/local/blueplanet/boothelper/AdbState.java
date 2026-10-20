package com.redmi.tvtoolkit;

/** Read-only evidence. ADB settings and a live TCP listener are separate conditions. */
final class AdbState {
    final int enabled;
    final String persistentPort, servicePort, serviceState, startup;

    AdbState(int enabled, String persistentPort, String servicePort, String serviceState, String startup) {
        this.enabled = enabled;
        this.persistentPort = persistentPort;
        this.servicePort = servicePort;
        this.serviceState = serviceState;
        this.startup = startup;
    }

    boolean networkReady() {
        return enabled == 1 && "5555".equals(persistentPort) && "5555".equals(servicePort)
                && "running".equals(serviceState);
    }

    boolean needsRepair() { return !networkReady(); }

    @Override public String toString() {
        return "adb_enabled=" + enabled + " persist.adb.tcp.port=" + persistentPort
                + " service.adb.tcp.port=" + servicePort + " init.svc.adbd=" + serviceState
                + " start_3rd_app=" + startup;
    }
}
