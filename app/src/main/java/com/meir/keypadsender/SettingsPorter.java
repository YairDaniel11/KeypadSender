package com.meir.keypadsender;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * ייצוא/ייבוא של התצורה כטקסט JSON (דרך הלוח), להעברה בין משתמשים/מכשירים.
 * בכוונה לא כולל: "יש רוט" ופקודת הרוט (פקודה שרצה כרוט - אסור לקבל מטקסט חיצוני),
 * וכן את הטיוטה וההיסטוריה.
 */
public final class SettingsPorter {
    private SettingsPorter() {}

    private static final int VERSION = 1;

    public static String export(Context c) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("v", VERSION);
        o.put("number", Prefs.getNumber(c));
        o.put("gap", Prefs.getGapMs(c));
        o.put("batch", Prefs.getBatch(c));
        o.put("map", Prefs.getLetterMap(c));
        int n = Prefs.getExtCount(c);
        JSONArray arr = new JSONArray();
        for (int i = 1; i <= n; i++) {
            JSONObject e = new JSONObject();
            e.put("label", Prefs.getExtLabel(c, i));
            e.put("number", Prefs.getExtNumber(c, i));
            e.put("seq", Prefs.getExtSeq(c, i));
            e.put("suffix", Prefs.getExtSuffix(c, i));
            arr.put(e);
        }
        o.put("ext", arr);
        return o.toString(2);
    }

    /** מייבא; זורק חריגה אם הטקסט אינו תצורה תקינה (ואז שום דבר לא משתנה). */
    public static void importFrom(Context c, String text) throws JSONException {
        JSONObject o = new JSONObject(text.trim());
        if (o.optInt("v", 0) != VERSION) throw new JSONException("version");
        JSONArray arr = o.getJSONArray("ext");
        int n = Math.max(1, Math.min(Prefs.MAX_EXT, arr.length()));
        String[] labels = new String[Prefs.MAX_EXT];
        String[] numbers = new String[Prefs.MAX_EXT];
        String[] seqs = new String[Prefs.MAX_EXT];
        String[] suffixes = new String[Prefs.MAX_EXT];
        for (int i = 0; i < Prefs.MAX_EXT; i++) {
            JSONObject e = i < arr.length() ? arr.getJSONObject(i) : new JSONObject();
            labels[i] = e.optString("label", "").trim();
            numbers[i] = cleanNumber(e.optString("number", ""));
            seqs[i] = SeqUtil.sanitize(e.optString("seq", ""));
            suffixes[i] = SeqUtil.sanitize(e.optString("suffix", Prefs.DEFAULT_SUFFIX));
        }
        // הכל תקין - עכשיו כותבים
        Prefs.saveExtConfig(c, n, labels, numbers, seqs, suffixes);
        Prefs.setNumber(c, cleanNumber(o.optString("number", "")));
        Prefs.setGapMs(c, o.optInt("gap", Prefs.DEFAULT_GAP_MS));
        Prefs.setBatch(c, o.optInt("batch", Prefs.DEFAULT_BATCH));
        Prefs.setLetterMap(c, o.optString("map", ""));
        Prefs.setExt(c, 1);
    }

    private static String cleanNumber(String s) {
        return s.replaceAll("[^0-9+*#\\-\\s]", "").trim();
    }
}
