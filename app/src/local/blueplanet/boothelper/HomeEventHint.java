package com.redmi.tvtoolkit;

/** Use a fresh, exact Xiaomi Home window event only when the active window has no readable root. */
final class HomeEventHint {
    static boolean matches(String pkg, String cls, long now, long eventAt) {
        if (eventAt <= 0 || now < eventAt || now - eventAt > 500) return false;
        return ("com.mitv.tvhome".equals(pkg)
                && ("com.mitv.tvhome.MainActivityUserMode".equals(cls)
                || ".MainActivityUserMode".equals(cls)))
                || ("com.xiaomi.mitv.settings".equals(pkg)
                && ("com.xiaomi.mitv.settings.entry.FallbackHome".equals(cls)
                || ".entry.FallbackHome".equals(cls)));
    }
}
