package com.redmi.tvtoolkit;

import java.util.HashMap;
import java.util.Map;

final class ConfigValues {
    static int revision(Map<String, String> values) {
        return Integer.parseInt(values.containsKey("revision") ? values.get("revision") : "0");
    }

    static Map<String, String> select(Map<String, String> old, String target, String fallback) {
        Map<String, String> next = new HashMap<>(old);
        next.put("enabled", "true");
        next.put("recovery", "true");
        next.put("target", target);
        next.put("fallback", target.equals(fallback) ? "" : fallback);
        next.put("revision", "" + (revision(old) + 1));
        next.put("phase", "awaiting_home");
        return next;
    }

    static Map<String, String> confirm(Map<String, String> old, String component, boolean fallback) {
        Map<String, String> next = new HashMap<>(old);
        next.put("lastGood", component);
        next.put("phase", fallback ? "fallback_foreground" : "target_foreground");
        if (fallback) {
            next.put("target", component);
            next.put("fallback", "");
            next.put("revision", "" + (revision(old) + 1));
        }
        return next;
    }

    static Map<String, String> disable(Map<String, String> old, String reason) {
        Map<String, String> next = new HashMap<>(old);
        next.put("enabled", "false");
        next.put("recovery", "false");
        next.put("revision", "" + (revision(old) + 1));
        next.put("phase", reason);
        return next;
    }
}
