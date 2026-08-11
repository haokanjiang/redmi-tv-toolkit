package local.blueplanet.boothelper;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

/** ADB-shell-only, allowlisted control entry used by setup and rollback. */
public final class ControlActivity extends Activity {
    private static final String TAG = "RedmiTvToolkit";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        final String action = getIntent().getStringExtra("action");
        final String value = getIntent().getStringExtra("value");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if ("repair".equals(action)) {
                        RecoveryActions.run(ControlActivity.this);
                        RecoveryActions.launchProjectivy(ControlActivity.this);
                    } else if ("restore-startup".equals(action)) {
                        XiaomiBridge.restoreStartupSetting(ControlActivity.this, value);
                    } else {
                        Log.w(TAG, "ignored unknown control action");
                    }
                } catch (Throwable error) {
                    Log.e(TAG, "control action failed", error);
                } finally {
                    finish();
                }
            }
        }, "toolkit-control").start();
    }
}

