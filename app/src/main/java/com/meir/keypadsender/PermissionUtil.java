package com.meir.keypadsender;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

public final class PermissionUtil {
    private PermissionUtil() {}

    public static final int REQUEST_CODE = 4001;

    /** כל ההרשאות שהתכונה צריכה - מבקשים את כולן מראש, ואם נדחו לא שולחים. */
    public static final String[] ALL = {
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.PROCESS_OUTGOING_CALLS
    };

    /** בטוח מכל Context. לפני API 23 כל ההרשאות מאושרות בהתקנה. */
    public static boolean hasAll(Context context, String[] permissions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        for (String p : permissions) {
            if (context.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    /** דורש Activity פעיל. */
    public static void requestAll(Activity activity, String[] permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(permissions, REQUEST_CODE);
        }
    }
}
