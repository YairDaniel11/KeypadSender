package com.meir.keypadsender;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/** חיוג לקו + שליחת השלוחה וההקשות כמחרוזת חיוג עם פסיקים (בלי רוט). */
public final class Dialer {
    private Dialer() {}

    /** מספר + רצף ההקשות של הכפתור (כולל פסיקים) + הגוף (הטקסט המקודד + סיומת). */
    public static String buildDialString(String number, String seq, String body) {
        return number.replaceAll("[\\s-]", "") + SeqUtil.sanitize(seq) + body;
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
