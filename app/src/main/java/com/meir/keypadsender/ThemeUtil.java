package com.meir.keypadsender;

import android.app.Activity;
import android.content.res.Configuration;

public final class ThemeUtil {
    private ThemeUtil() {}

    /** לקרוא ב-onCreate, לפני setContentView. */
    public static void applyTheme(Activity activity) {
        activity.setTheme(isDark(activity) ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
    }

    /** צבע לפי attr של הערכה הנוכחית. */
    public static int resolveColor(android.content.Context c, int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        c.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private static boolean isDark(Activity activity) {
        int night = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        if (night == Configuration.UI_MODE_NIGHT_YES) return true;
        if (night == Configuration.UI_MODE_NIGHT_NO) return false;
        return true; // לא ידוע (מכשיר ישן) -> כהה
    }
}
