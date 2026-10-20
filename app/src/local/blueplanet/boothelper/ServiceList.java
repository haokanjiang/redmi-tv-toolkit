package com.redmi.tvtoolkit;

import java.util.LinkedHashSet;
import java.util.Set;

final class ServiceList {
    static String normalize(String raw) {
        int slash = raw.indexOf('/');
        if (slash > 0 && raw.substring(slash + 1).startsWith(".")) {
            return raw.substring(0, slash + 1) + raw.substring(0, slash) + raw.substring(slash + 1);
        }
        return raw;
    }

    static String merge(String raw, String service, boolean remove) {
        Set<String> entries = new LinkedHashSet<>();
        if (raw != null && !raw.equals("null")) for (String item : raw.split(":")) {
            if (!item.isEmpty() && !normalize(item).equals(normalize(service))) entries.add(item);
        }
        if (!remove) entries.add(service);
        StringBuilder result = new StringBuilder();
        for (String entry : entries) { if (result.length() > 0) result.append(':'); result.append(entry); }
        return result.toString();
    }
}
