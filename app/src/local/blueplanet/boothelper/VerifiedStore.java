package com.redmi.tvtoolkit;

import java.util.HashMap;
import java.util.Map;

/** A successful update includes read-back; failures restore and verify the old snapshot. */
final class VerifiedStore {
    interface Backend {
        Map<String, String> read();
        boolean write(Map<String, String> values);
    }

    static void replace(Backend store, Map<String, String> next) {
        Map<String, String> before = new HashMap<>(store.read());
        try {
            if (!store.write(next) || !next.equals(store.read())) {
                throw new IllegalStateException("configuration write/read-back failed");
            }
        } catch (RuntimeException error) {
            if (!store.write(before) || !before.equals(store.read())) {
                throw new IllegalStateException("configuration rollback failed", error);
            }
            throw error;
        }
    }
}
