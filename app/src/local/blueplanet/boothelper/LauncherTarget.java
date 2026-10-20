package com.redmi.tvtoolkit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/** Validate the exact exported entry, including category and disabled component overrides. */
final class LauncherTarget {
    final ComponentName component;
    final String category;

    private LauncherTarget(ComponentName component, String category) {
        this.component = component;
        this.category = category;
    }

    static LauncherTarget resolve(Context context, String raw) {
        ComponentName component = raw == null ? null : ComponentName.unflattenFromString(raw);
        if (component == null) throw new IllegalArgumentException("invalid component");
        String pkg = component.getPackageName();
        if (pkg.equals(context.getPackageName()) || pkg.equals("android")
                || pkg.equals("com.mitv.tvhome") || pkg.equals("com.xiaomi.mitv.settings")) {
            throw new IllegalArgumentException("system/helper component is not an override target");
        }
        PackageManager pm = context.getPackageManager();
        for (String category : new String[]{Intent.CATEGORY_HOME, Intent.CATEGORY_LEANBACK_LAUNCHER}) {
            // An explicit intent skips filter matching. Query package+category and compare exact components.
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(pkg);
            for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
                ActivityInfo activity = info.activityInfo;
                if (activity != null && component.equals(new ComponentName(activity.packageName, activity.name))
                        && activity.exported && activity.enabled && activity.applicationInfo.enabled
                        && (activity.permission == null || context.checkSelfPermission(activity.permission)
                            == PackageManager.PERMISSION_GRANTED)) {
                    return new LauncherTarget(component, category);
                }
            }
        }
        throw new IllegalArgumentException("component is missing, disabled, unexported or not HOME/LEANBACK");
    }

    Intent intent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(category).setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
}
