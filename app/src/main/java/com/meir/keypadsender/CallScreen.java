package com.meir.keypadsender;

import android.content.Context;
import android.os.Build;
import android.telecom.TelecomManager;

/** מעביר את מסך השיחה הפעילה לחזית. */
final class CallScreen {
    private CallScreen() {}

    /** דרך TelecomManager (API 23+, בלי רוט). true = הבקשה נשלחה. */
    static boolean showViaTelecom(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        try {
            TelecomManager tm = (TelecomManager) ctx.getSystemService(Context.TELECOM_SERVICE);
            if (tm == null) return false;
            tm.showInCallScreen(false);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** פקודות רוט לגיבוי, לפי סדר ניסיון (אחרי הפקודה שהוגדרה בהגדרות). */
    static final String[] FALLBACK_CMDS = {
            "am start -n com.android.phone/.InCallScreen",
            "am start -a android.intent.action.MAIN -n com.android.incallui/.InCallActivity",
            "am start -n com.google.android.dialer/com.android.incallui.InCallActivity",
            "am start -n com.android.dialer/com.android.incallui.InCallActivity"
    };
}
