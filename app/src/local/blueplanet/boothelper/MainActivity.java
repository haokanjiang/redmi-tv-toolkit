package local.blueplanet.boothelper;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final String TAG = "RedmiTvToolkit";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        TextView status = new TextView(this);
        status.setText("正在恢复 Projectivy、无障碍和网络 ADB……");
        status.setTextSize(26f);
        status.setPadding(64, 64, 64, 64);
        setContentView(status);

        if (!DeviceGuard.isSupportedDevice()) {
            status.setText("此恢复助手只支持已验证的 blueplanet 固件，未执行修改。");
            Log.e(TAG, "unsupported device; recovery blocked");
            return;
        }

        RecoveryActions.prepareSoftWakeNow(this);
        RecoveryService.start(this);
        if (!requestDeviceAdminIfInactive()) {
            RecoveryActions.launchProjectivy(this);
            finish();
        }
    }

    private boolean requestDeviceAdminIfInactive() {
        try {
            DevicePolicyManager manager = (DevicePolicyManager)
                    getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, BootAdminReceiver.class);
            if (manager != null && !manager.isAdminActive(admin)
                    && !manager.isDeviceOwnerApp(getPackageName())) {
                Intent request = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                request.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
                request.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "保持开机恢复 ADB、无障碍和 Projectivy；不使用任何管理策略。");
                startActivity(request);
                return true;
            }
        } catch (Throwable error) {
            Log.i(TAG, "device admin activation skipped", error);
        }
        return false;
    }
}

