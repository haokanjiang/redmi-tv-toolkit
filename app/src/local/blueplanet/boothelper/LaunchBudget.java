package com.redmi.tvtoolkit;

/** Separate from a successful episode: repeated crashes must not reset the rolling circuit breaker. */
final class LaunchBudget {
    private long start = -1;
    private int count;
    boolean claim(long now) {
        if (start < 0 || now - start >= 60000) { start = now; count = 0; }
        return ++count <= 10;
    }
}
