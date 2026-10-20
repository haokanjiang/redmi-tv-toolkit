package com.redmi.tvtoolkit;

/** Event-driven, bounded foreground verification. The timer revisits suppressed duplicate events. */
public final class HomeOverrideRetry {
    enum Foreground { SYSTEM, TARGET, FALLBACK, OTHER, UNKNOWN }
    enum Action { NONE, TARGET, FALLBACK, CONFIRMED_TARGET, CONFIRMED_FALLBACK, DISABLE }
    static final long WINDOW_MS = 10000, VERIFY_MS = 1000, STABLE_MS = 1000;
    static final int MAX_TARGET = 3, MAX_FALLBACK = 2;
    private boolean active, failed, usingFallback;
    private int targets, fallbacks;
    private long started, requested, stableSince = -1;

    boolean active() { return active; }
    void reset() { active = failed = usingFallback = false; targets = fallbacks = 0; stableSince = -1; }
    void launchFailed() { failed = true; }

    Action step(long now, Foreground foreground, boolean hasFallback) {
        if (!active) {
            if (foreground != Foreground.SYSTEM) return Action.NONE;
            active = true;
            started = now;
            requested = now;
            targets = 1;
            return Action.TARGET;
        }
        if (foreground == Foreground.OTHER) {
            reset(); // Never pull the user away from Settings or a different app.
            return Action.NONE;
        }
        if ((!usingFallback && foreground == Foreground.TARGET)
                || (usingFallback && foreground == Foreground.FALLBACK)) {
            if (stableSince < 0) stableSince = now;
            if (now - stableSince >= STABLE_MS) {
                Action result = usingFallback ? Action.CONFIRMED_FALLBACK : Action.CONFIRMED_TARGET;
                reset();
                return result;
            }
            return Action.NONE;
        }
        stableSince = -1;
        if (now - started >= WINDOW_MS) { reset(); return Action.DISABLE; }
        if (!failed && now - requested < VERIFY_MS) return Action.NONE;
        failed = false;
        requested = now;
        if (!usingFallback && targets < MAX_TARGET) { targets++; return Action.TARGET; }
        usingFallback = true;
        if (hasFallback && fallbacks < MAX_FALLBACK) { fallbacks++; return Action.FALLBACK; }
        reset();
        return Action.DISABLE;
    }
}
