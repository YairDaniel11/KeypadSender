package com.meir.keypadsender;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;

/**
 * עוקב אחרי "עם מי אני בשיחה". אין API ציבורי שנותן את זה (במיוחד באנדרואיד 4.4),
 * ולכן מאזינים לשידורי NEW_OUTGOING_CALL (מספר שחויג) ו-PHONE_STATE (מצב + מספר נכנס),
 * שומרים ב-SharedPreferences, ובודקים מול TelephonyManager.getCallState() החי.
 */
public final class CallTracker {
    private CallTracker() {}

    public enum Status { IDLE, IN_CALL_TARGET, IN_CALL_OTHER, IN_CALL_UNKNOWN }

    private static final String K_OUT = "ct_out";
    private static final String K_IN = "ct_in";
    private static final String K_ACTIVE = "ct_active";
    private static final String K_LAST = "ct_last";

    private static final int S_IDLE = 0;
    private static final int S_RINGING = 1;
    private static final int S_OFFHOOK = 2;

    private static final String K_OWN_TS = "ct_own_ts";
    private static final long OWN_DIAL_MS = 20000;

    /**
     * האפליקציה עצמה מחייגת: היא יודעת בוודאות עם מי השיחה, בלי להסתמך על שידורי המערכת
     * (שלא תמיד מגיעים, ולפעמים מגיעים עם מספר ששונה - קידומת, פורמט וכו').
     */
    public static void onAppDial(Context ctx, String number) {
        String n = PhoneNumberUtil.normalize(number);
        Prefs.raw(ctx).edit().putString(K_OUT, n).putString(K_ACTIVE, n).putInt(K_LAST, S_IDLE)
                .putString(K_IN, "").putLong(K_OWN_TS, System.currentTimeMillis()).apply();
    }

    public static void onOutgoing(Context ctx, String rawNumber) {
        String n = PhoneNumberUtil.normalize(rawNumber);
        SharedPreferences sp = Prefs.raw(ctx);
        // שידור על חיוג שהאפליקציה עצמה ביצעה הרגע: משאירים את המספר שהיא רשמה
        if (System.currentTimeMillis() - sp.getLong(K_OWN_TS, 0) < OWN_DIAL_MS) return;
        SharedPreferences.Editor e = sp.edit().putString(K_OUT, n);
        // חיוג בזמן שיחה פעילה: הצד הפעיל הוא כעת הצד החדש
        if (sp.getInt(K_LAST, S_IDLE) == S_OFFHOOK) e.putString(K_ACTIVE, n);
        e.apply();
    }

    public static void onPhoneState(Context ctx, String state, String incomingNumber) {
        SharedPreferences sp = Prefs.raw(ctx);
        int last = sp.getInt(K_LAST, S_IDLE);
        SharedPreferences.Editor e = sp.edit();

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            if (last != S_OFFHOOK) { // שיחה ממתינה בזמן שיחה - מתעלמים
                String n = PhoneNumberUtil.normalize(incomingNumber);
                if (n.length() > 0) e.putString(K_IN, n); // השידור הראשון לעיתים בלי מספר
                e.putInt(K_LAST, S_RINGING);
            }
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            if (last == S_RINGING) {
                e.putString(K_ACTIVE, sp.getString(K_IN, ""));
            } else if (last == S_IDLE) {
                e.putString(K_ACTIVE, sp.getString(K_OUT, ""));
            } // last == OFFHOOK: שידור כפול, לא משנים
            e.putInt(K_LAST, S_OFFHOOK);
        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            e.putString(K_OUT, "").putString(K_IN, "").putString(K_ACTIVE, "").putInt(K_LAST, S_IDLE);
        }
        e.apply();
    }

    public static Status status(Context ctx) {
        TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null || tm.getCallState() != TelephonyManager.CALL_STATE_OFFHOOK) {
            return Status.IDLE; // לא בשיחה (או רק מצלצל)
        }
        String active = Prefs.raw(ctx).getString(K_ACTIVE, "");
        if (active.length() == 0) return Status.IN_CALL_UNKNOWN;
        return isAnyConfigured(ctx, active) ? Status.IN_CALL_TARGET : Status.IN_CALL_OTHER;
    }

    private static boolean isAnyConfigured(Context ctx, String active) {
        if (PhoneNumberUtil.same(active, Prefs.getNumber(ctx))) return true;
        for (int i = 1; i <= Prefs.getExtCount(ctx); i++) {
            String n = Prefs.getExtNumber(ctx, i);
            if (n.length() > 0 && PhoneNumberUtil.same(active, n)) return true;
        }
        return false;
    }

    /** האם השיחה הפעילה היא עם המספר הנתון (הכפתור הנבחר). */
    public static boolean activeMatches(Context ctx, String number) {
        return PhoneNumberUtil.same(Prefs.raw(ctx).getString(K_ACTIVE, ""), number);
    }
}
