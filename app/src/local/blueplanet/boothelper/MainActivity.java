package com.redmi.tvtoolkit;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.util.Log;

public final class MainActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        DeviceProfile profile = DeviceProfile.detect();
        HomeOverrideConfig config = HomeOverrideConfig.load(this);
        TextView view = new TextView(this);
        view.setTextSize(22); view.setPadding(48, 48, 48, 48);
        view.setText("Redmi TV Toolkit\nProfile: " + profile.id + "\nOverride: " + config.enabled
                + "\nTarget: " + config.target + "\nRecovery: " + config.recoveryEnabled);
        setContentView(view);
        try { RecoveryScheduler.ensure(this, true); }
        catch (RuntimeException error) { Log.e(RecoveryActions.TAG, "recovery scheduling failed", error); }
        // Retain blueplanet's visible startup mediator. No boot receiver launches Activities.
        if (profile.capabilities.startThirdApp && config.recoveryEnabled && !config.target.isEmpty()) {
            try { startActivity(LauncherTarget.resolve(this, config.target).intent()); finish(); }
            catch (RuntimeException error) { Log.e(RecoveryActions.TAG, "startup target rejected", error); }
        }
    }
}
