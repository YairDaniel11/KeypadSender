package com.meir.keypadsender;

import java.util.ArrayList;
import java.util.List;

/**
 * רצף הקשות בסגנון מחרוזת חיוג: ספרות, * ו-# הם מקשים, ופסיק = המתנה של ~2 שניות.
 * מחלקה טהורה (בלי אנדרואיד) כדי שאפשר לבדוק אותה.
 */
public final class SeqUtil {
    private SeqUtil() {}

    public static final long COMMA_MS = 2000L;
    private static final int MAX_COMMAS = 30;

    /** משאיר רק 0-9 * # ,  (בלי רווחים ושאר תווים). */
    public static String sanitize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '*' || c == '#' || c == ',') sb.append(c);
        }
        return sb.toString();
    }

    private static String commas(int n) {
        StringBuilder sb = new StringBuilder();
        int c = Math.max(0, Math.min(MAX_COMMAS, n));
        for (int i = 0; i < c; i++) sb.append(',');
        return sb.toString();
    }

    /** ברירת המחדל הישנה: [p1] שלוחה [p2] תת-שלוחה [p3]. */
    public static String defaultSeq(int p1, int ext, int p2, int sub, int p3) {
        return commas(p1) + ext + commas(p2) + sub + commas(p3);
    }

    /** תוצאת פירוק: חלקים לשליחה והשהיה (מ"ש) לפני כל חלק. */
    public static final class Script {
        public final String[] parts;
        public final long[] delays;
        Script(List<String> p, List<Long> d) {
            parts = p.toArray(new String[0]);
            delays = new long[d.size()];
            for (int i = 0; i < delays.length; i++) delays[i] = d.get(i);
        }
    }

    /** מפרק רצף + גוף (הטקסט המקודד כולל הסיומת) לחלקים עם השהיות. */
    public static Script toScript(String seq, String body) {
        List<String> parts = new ArrayList<String>();
        List<Long> delays = new ArrayList<Long>();
        StringBuilder cur = new StringBuilder();
        long curDelay = 0;
        long wait = 0;
        String s = sanitize(seq) + sanitize(body); // גם בגוף (הסיומת) פסיק = המתנה
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ',') {
                if (cur.length() > 0) {
                    parts.add(cur.toString());
                    delays.add(curDelay);
                    cur.setLength(0);
                }
                wait += COMMA_MS;
            } else {
                if (cur.length() == 0) { curDelay = wait; wait = 0; }
                cur.append(c);
            }
        }
        if (cur.length() > 0) { parts.add(cur.toString()); delays.add(curDelay); }
        if (parts.isEmpty()) { parts.add(""); delays.add(wait); }
        return new Script(parts, delays);
    }
}
