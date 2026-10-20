package com.redmi.tvtoolkit;

/** Persisted by the Android adapter. Duplicate signals cannot reset a boot's repair budget. */
final class RecoveryBudget {
    static final int MAX = 4;
    static boolean allowed(int boot, int recordedBoot, int attempts, long now, long last) {
        if (boot < 0) return false;
        return boot != recordedBoot || (attempts < MAX && (last < 0 || now - last >= 10000));
    }
}
