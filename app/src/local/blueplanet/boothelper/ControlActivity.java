package com.redmi.tvtoolkit;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

/** Retained shell-only legacy entry; all mutations use the same persisted recovery policy. */
public final class ControlActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            String action = getIntent().getStringExtra("action");
            if ("repair".equals(action)) RecoveryScheduler.ensure(this, true);
            else if ("restore".equals(action)) RecoveryActions.restore(this, "restored");
        } catch (RuntimeException error) { Log.e(RecoveryActions.TAG, "control activity failed", error); }
        finish();
    }
}
