package com.meir.keypadsender;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

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
    private static final String K_DRAFT = "draft";
    private static final String K_HIST = "history";
    private static final String SEP = "\u001F";
    private static final int MAX_HISTORY = 50;

    public static SharedPreferences raw(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String getNumber(Context c) { return raw(c).getString(K_NUMBER, "").trim(); }
    public static int getP1(Context c) { return raw(c).getInt(K_P1, DEFAULT_P1); }
    public static int getP2(Context c) { return raw(c).getInt(K_P2, DEFAULT_P2); }
    public static int getP3(Context c) { return raw(c).getInt(K_P3, DEFAULT_P3); }
    public static int getExt(Context c) {
        return Math.max(1, Math.min(getExtCount(c), raw(c).getInt(K_EXT, 1)));
    }

    // ---- הגדרת הכפתורים (שלוחות) במסך הבית ----
    public static final int MAX_EXT = 9;
    public static final int DEFAULT_EXT_COUNT = 3;
    public static final String DEFAULT_SUFFIX = "#";
    private static final String[] DEFAULT_LABELS = {"1 חיפוש", "2 מוזיקה", "3 מדיה"};

    public static int getExtCount(Context c) {
        return Math.max(1, Math.min(MAX_EXT, raw(c).getInt("ext_count", DEFAULT_EXT_COUNT)));
    }

    /** כותרת הכפתור i (1..9); ריק = בלי כותרת (יוצג "#i"). */
    public static String getExtLabel(Context c, int i) {
        String def = i >= 1 && i <= DEFAULT_LABELS.length ? DEFAULT_LABELS[i - 1] : "";
        return raw(c).getString("ext_label_" + i, def).trim();
    }

    public static String getExtTitle(Context c, int i) {
        String t = getExtLabel(c, i);
        return t.length() > 0 ? t : "#" + i;
    }

    /** מספר ייעודי לכפתור; ריק = משתמשים במספר הכללי. */
    public static String getExtNumber(Context c, int i) {
        return raw(c).getString("ext_number_" + i, "").trim();
    }

    /** המספר בפועל לכפתור i. */
    public static String effectiveNumber(Context c, int i) {
        String n = getExtNumber(c, i);
        return n.length() > 0 ? n : getNumber(c);
    }

    /** רצף ההקשות שלפני הטקסט: ספרות, * #, ופסיק = המתנה של ~2 שניות. ברירת מחדל = ההתנהגות הישנה. */
    public static String getExtSeq(Context c, int i) {
        String def = SeqUtil.defaultSeq(getP1(c), i, getP2(c), 2, getP3(c));
        return SeqUtil.sanitize(raw(c).getString("ext_seq_" + i, def));
    }

    /** מה נשלח אחרי הטקסט (ברירת מחדל #). */
    public static String getExtSuffix(Context c, int i) {
        return SeqUtil.sanitize(raw(c).getString("ext_suffix_" + i, DEFAULT_SUFFIX));
    }

    public static void saveExtConfig(Context c, int count, String[] labels, String[] numbers,
                                     String[] seqs, String[] suffixes) {
        SharedPreferences.Editor e = raw(c).edit();
        e.putInt("ext_count", Math.max(1, Math.min(MAX_EXT, count)));
        for (int i = 1; i <= MAX_EXT; i++) {
            e.putString("ext_label_" + i, labels[i - 1]);
            e.putString("ext_number_" + i, numbers[i - 1]);
            e.putString("ext_seq_" + i, SeqUtil.sanitize(seqs[i - 1]));
            e.putString("ext_suffix_" + i, SeqUtil.sanitize(suffixes[i - 1]));
        }
        e.apply();
    }

    /** התאמות למפת האותיות (ראו KeypadEncoder.parseMap); ריק = ברירת מחדל. */
    public static String getLetterMap(Context c) { return raw(c).getString("letter_map", ""); }
    public static void setLetterMap(Context c, String s) { raw(c).edit().putString("letter_map", s).apply(); }
    public static void setNumber(Context c, String n) { raw(c).edit().putString(K_NUMBER, n).apply(); }
    public static void setGapMs(Context c, int ms) { raw(c).edit().putInt(K_GAP, Math.max(0, ms)).apply(); }
    public static boolean useRoot(Context c) { return raw(c).getBoolean(K_ROOT, false); }
    public static int getGapMs(Context c) { return raw(c).getInt(K_GAP, DEFAULT_GAP_MS); }
    public static final int DEFAULT_BATCH = 5;
    public static int getBatch(Context c) { return raw(c).getInt("batch", DEFAULT_BATCH); }
    public static void setBatch(Context c, int n) { raw(c).edit().putInt("batch", Math.max(1, n)).apply(); }
    public static String getInCallCmd(Context c) { return raw(c).getString(K_CMD, DEFAULT_INCALL_CMD); }

    /** הטקסט שבחלונית: נשמר בכל שינוי, כדי ששום יציאה/הריגת תהליך לא תמחק אותו. */
    public static String getDraft(Context c) { return raw(c).getString(K_DRAFT, ""); }
    public static void setDraft(Context c, String t) { raw(c).edit().putString(K_DRAFT, t).apply(); }

    /** היסטוריית טקסטים שנשלחו: החדש ראשון, בלי כפילויות. */
    public static List<String> getHistory(Context c) {
        List<String> out = new ArrayList<String>();
        String raw = raw(c).getString(K_HIST, "");
        if (raw.length() == 0) return out;
        for (String t : raw.split(SEP)) if (t.length() > 0) out.add(t);
        return out;
    }

    private static void saveHistory(Context c, List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String t : l) {
            if (sb.length() > 0) sb.append(SEP);
            sb.append(t);
        }
        raw(c).edit().putString(K_HIST, sb.toString()).apply();
    }

    public static void addHistory(Context c, String text) {
        String t = text == null ? "" : text.trim().replace(SEP, " ");
        if (t.length() == 0) return;
        List<String> l = getHistory(c);
        l.remove(t);
        l.add(0, t);
        while (l.size() > MAX_HISTORY) l.remove(l.size() - 1);
        saveHistory(c, l);
    }

    public static void removeHistory(Context c, int index) {
        List<String> l = getHistory(c);
        if (index >= 0 && index < l.size()) {
            l.remove(index);
            saveHistory(c, l);
        }
    }

    public static void clearHistory(Context c) { raw(c).edit().remove(K_HIST).apply(); }

    /** "קו עם פרסומות": מחייגים ושולחים רק את השלוחה, והשאר אחרי אישור. */
    public static boolean isAdsLine(Context c) { return raw(c).getBoolean("ads_line", false); }
    public static void setAdsLine(Context c, boolean on) { raw(c).edit().putBoolean("ads_line", on).apply(); }

    public static void setExt(Context c, int ext) { raw(c).edit().putInt(K_EXT, ext).apply(); }

    public static void saveSettings(Context c, String number,
                                    boolean root, int gapMs, String cmd) {
        raw(c).edit()
                .putString(K_NUMBER, number)
                .putBoolean(K_ROOT, root)
                .putInt(K_GAP, gapMs)
                .putString(K_CMD, cmd)
                .apply();
    }
}
