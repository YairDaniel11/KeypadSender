package com.meir.keypadsender;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/** חיוג לקו + שליחת השלוחה וההקשות כמחרוזת חיוג עם פסיקים (בלי רוט). */
public final class Dialer {
    private Dialer() {}

    private static String commas(int n) {
        StringBuilder sb = new StringBuilder();
        int c = Math.max(0, Math.min(30, n));
        for (int i = 0; i < c; i++) sb.append(',');
        return sb.toString();
    }

    /**
     * מספר + [p1 פסיקים] + שלוחה + [p2] + 2 (תת-שלוחה: הקלדה) + [p3] + ההקשות (כולל #).
     * כל פסיק = השהיה של כ-2 שניות אחרי שהשיחה נענתה.
     */
    public static String buildDialString(String number, int p1, int p2, int p3, int ext, String body) {
        String clean = number.replaceAll("[\\s-]", "");
        return clean + commas(p1) + ext + commas(p2) + "2" + commas(p3) + body;
    }

    /** false = מצב טיסה, לא מחייגים. דורש CALL_PHONE. */
    public static boolean dial(Context ctx, String dialString) {
        if (isAirplaneMode(ctx)) return false;
        Intent intent = new Intent(Intent.ACTION_CALL,
                Uri.parse("tel:" + dialString.replace("#", "%23")));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
        return true;
    }

    @SuppressWarnings("deprecation")
    private static boolean isAirplaneMode(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return Settings.Global.getInt(ctx.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        }
        return Settings.System.getInt(ctx.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }
}
