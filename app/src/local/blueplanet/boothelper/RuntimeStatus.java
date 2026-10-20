package com.redmi.tvtoolkit;

final class RuntimeStatus {
    static volatile boolean bound;
    static volatile int requestedRevision = -1;
    static volatile String foreground = "";
    static volatile String outcome = "idle";
}
