package com.meir.keypadsender;

import android.content.Context;
import android.content.SharedPreferences;

/** מקור אמת יחיד להגדרות (וגם למצב השיחה, ראו CallTracker). */
public final class Prefs {
    private Prefs() {}

    public static final String NAME = "keypad_prefs";

    public static final String DEFAULT_INCALL_CMD = "am start -n com.android.phone/.InCallScreen";
    public static final int DEFAULT_P1 = 3;
    public static final int DEFAULT_P2 = 2;
    public static final int DEFAULT_P3 = 2;
    public static final int DEFAULT_GAP_MS = 250;

    private static final String K_NUMBER = "number";
    private static final String K_P1 = "p1";
    private static final String K_P2 = "p2";
    private static final String K_P3 = "p3";
    private static final String K_EXT = "ext";
    private static final String K_ROOT = "root";
    private static final String K_GAP = "gap";
    private static final String K_CMD = "incall_cmd";

    public static SharedPreferences raw(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String getNumber(Context c) { return raw(c).getString(K_NUMBER, "").trim(); }
    public static int getP1(Context c) { return raw(c).getInt(K_P1, DEFAULT_P1); }
    public static int getP2(Context c) { return raw(c).getInt(K_P2, DEFAULT_P2); }
    public static int getP3(Context c) { return raw(c).getInt(K_P3, DEFAULT_P3); }
    public static int getExt(Context c) { return raw(c).getInt(K_EXT, 1); }
    public static boolean useRoot(Context c) { return raw(c).getBoolean(K_ROOT, false); }
    public static int getGapMs(Context c) { return raw(c).getInt(K_GAP, DEFAULT_GAP_MS); }
    public static String getInCallCmd(Context c) { return raw(c).getString(K_CMD, DEFAULT_INCALL_CMD); }

    public static void setExt(Context c, int ext) { raw(c).edit().putInt(K_EXT, ext).apply(); }

    public static void saveSettings(Context c, String number, int p1, int p2, int p3,
                                    boolean root, int gapMs, String cmd) {
        raw(c).edit()
                .putString(K_NUMBER, number)
                .putInt(K_P1, p1)
                .putInt(K_P2, p2)
                .putInt(K_P3, p3)
                .putBoolean(K_ROOT, root)
                .putInt(K_GAP, gapMs)
                .putString(K_CMD, cmd)
                .apply();
    }
}
