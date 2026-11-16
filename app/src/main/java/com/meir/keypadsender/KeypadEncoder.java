package com.meir.keypadsender;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * ממיר טקסט עברי למחרוזת הקשות של טלפון מקשים ישן (ראו docs/SPEC.md).
 * מחלקה טהורה (בלי תלות באנדרואיד) כדי שאפשר לבדוק אותה כ-unit test רגיל.
 *
 * כללים:
 *  - אות = המקש שלה, חוזר לפי מספר ההקשות. אותיות סופיות נשלחות כרגילות (המכשיר מוסיף אוטומטית).
 *  - רווח = 0, הספרה 0 = 00, מחיקה = 000 (האפליקציה לא שולחת מחיקה כי הטקסט כבר סופי).
 *  - הספרה 1 = 11, ספרה 2-9 = חמש הקשות על המקש.
 *  - 1 בודד מפריד בין שני תווים רצופים על אותו מקש (פרט למקש 1 עצמו).
 *  - # בסוף = סיום ושליחה.
 */
public final class KeypadEncoder {
    private KeypadEncoder() {}

    public static final char END = '#';
    public static final String DELETE = "000";

    private static final int BASE = '\u05D0'; // א
    private static final char[] KEY = new char[32];
    private static final int[] PRESSES = new int[32];

    static {
        put('3', 1, '\u05D0'); put('3', 2, '\u05D1'); put('3', 3, '\u05D2'); // א ב ג
        put('2', 1, '\u05D3'); put('2', 2, '\u05D4'); put('2', 3, '\u05D5'); // ד ה ו
        put('6', 1, '\u05D6'); put('6', 2, '\u05D7'); put('6', 3, '\u05D8'); // ז ח ט
        put('5', 1, '\u05D9'); put('5', 2, '\u05DB'); put('5', 4, '\u05DC'); // י כ ל
        put('4', 1, '\u05DE'); put('4', 3, '\u05E0');                         // מ נ
        put('9', 1, '\u05E1'); put('9', 2, '\u05E2'); put('9', 3, '\u05E4'); // ס ע פ
        put('8', 1, '\u05E6'); put('8', 3, '\u05E7');                         // צ ק
        put('7', 1, '\u05E8'); put('7', 2, '\u05E9'); put('7', 3, '\u05EA'); // ר ש ת
    }

    private static void put(char key, int presses, char letter) {
        KEY[letter - BASE] = key;
        PRESSES[letter - BASE] = presses;
    }

    /** ך->כ, ם->מ, ן->נ, ף->פ, ץ->צ */
    private static char normalizeFinal(char c) {
        switch (c) {
            case '\u05DA': return '\u05DB';
            case '\u05DD': return '\u05DE';
            case '\u05DF': return '\u05E0';
            case '\u05E3': return '\u05E4';
            case '\u05E5': return '\u05E6';
            default: return c;
        }
    }

    private static boolean isMappedLetter(char c) {
        return c >= '\u05D0' && c <= '\u05EA' && KEY[c - BASE] != 0;
    }

    /** כל רצף רווחים/שורות הופך לרווח יחיד, בלי רווחים בקצוות. */
    public static String normalizeText(String text) {
        if (text == null) return "";
        return text.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
    }

    /** מחזיר מחרוזת מקשים כולל # בסוף. תווים לא נתמכים מדולגים. */
    public static String encode(String text) {
        return encodeBody(text, null) + END;
    }

    private static String cachedSpec = "";
    private static java.util.Map<Character, int[]> cachedMap = new java.util.HashMap<Character, int[]>();

    /**
     * התאמות למפת האותיות: רשימה מופרדת ברווח/פסיק של "אות=מקש:הקשות", למשל "a=2:1 b=2:2 ש=7:1".
     * נוספות/דורסות את ברירת המחדל. רשומות לא תקינות מדולגות.
     */
    static synchronized java.util.Map<Character, int[]> parseMap(String spec) {
        String sp = spec == null ? "" : spec;
        if (sp.equals(cachedSpec)) return cachedMap;
        java.util.Map<Character, int[]> m = new java.util.HashMap<Character, int[]>();
        for (String tok : sp.trim().split("[\\s,]+")) {
            if (tok.length() != 5 || tok.charAt(1) != '=' || tok.charAt(3) != ':') continue;
            char k = tok.charAt(2), pr = tok.charAt(4);
            if (k < '0' || k > '9' || pr < '1' || pr > '9') continue;
            m.put(tok.charAt(0), new int[]{k, pr - '0'});
        }
        cachedSpec = sp;
        cachedMap = m;
        return m;
    }

    /** כמו encode אבל בלי סיומת, ועם התאמות מפה (null = ברירת מחדל). */
    public static String encodeBody(String text, String mapSpec) {
        java.util.Map<Character, int[]> custom = parseMap(mapSpec);
        String t = normalizeText(text);
        StringBuilder out = new StringBuilder();
        char lastKey = 0;

        for (int i = 0; i < t.length(); i++) {
            char c = normalizeFinal(t.charAt(i));
            char key;
            int presses;
            int[] cu = custom.get(t.charAt(i));
            if (cu == null) cu = custom.get(c);

            if (cu != null) {
                key = (char) cu[0]; presses = cu[1];
            } else if (c == ' ') {
                key = '0'; presses = 1;
            } else if (c == '0') {
                key = '0'; presses = 2;
            } else if (c == '1') {
                key = '1'; presses = 2;
            } else if (c >= '2' && c <= '9') {
                key = c; presses = 5;
            } else if (isMappedLetter(c)) {
                key = KEY[c - BASE]; presses = PRESSES[c - BASE];
            } else {
                continue; // לא נתמך
            }

            if (key != '1' && key == lastKey) out.append('1'); // מפריד
            for (int p = 0; p < presses; p++) out.append(key);
            lastKey = key;
        }
        return out.toString();
    }

    /** טבלת ההקשות בפועל (ברירת המחדל + התאמות המשתמש), לעזרה: שורה לכל מקש. */
    public static String describeTable(String mapSpec) {
        java.util.Map<Character, int[]> custom = parseMap(mapSpec);
        java.util.TreeMap<Character, java.util.List<int[]>> byKey =
                new java.util.TreeMap<Character, java.util.List<int[]>>();
        java.util.Map<Character, int[]> all = new java.util.LinkedHashMap<Character, int[]>();
        for (int i = 0; i < KEY.length; i++) {
            if (KEY[i] != 0) all.put((char) (BASE + i), new int[]{KEY[i], PRESSES[i]});
        }
        all.putAll(custom);
        for (java.util.Map.Entry<Character, int[]> e : all.entrySet()) {
            Character k = (char) e.getValue()[0];
            java.util.List<int[]> l = byKey.get(k);
            if (l == null) { l = new java.util.ArrayList<int[]>(); byKey.put(k, l); }
            l.add(new int[]{e.getKey(), e.getValue()[1]});
        }
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<Character, java.util.List<int[]>> e : byKey.entrySet()) {
            java.util.List<int[]> l = e.getValue();
            java.util.Collections.sort(l, new java.util.Comparator<int[]>() {
                @Override public int compare(int[] a, int[] b) { return a[1] - b[1]; }
            });
            sb.append("מקש ").append(e.getKey()).append(":  ");
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append("   ");
                sb.append((char) l.get(i)[0]).append(" = ").append(l.get(i)[1]);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** תווים שהמרה תדלג עליהם (ללא כפילויות), לתצוגת אזהרה. */
    public static String findUnsupported(String text) {
        return findUnsupported(text, null);
    }

    public static String findUnsupported(String text, String mapSpec) {
        java.util.Map<Character, int[]> custom = parseMap(mapSpec);
        String t = normalizeText(text);
        Set<Character> bad = new LinkedHashSet<Character>();
        for (int i = 0; i < t.length(); i++) {
            char c = normalizeFinal(t.charAt(i));
            boolean ok = custom.containsKey(t.charAt(i)) || custom.containsKey(c) || c == ' ' || (c >= '0' && c <= '9') || isMappedLetter(c);
            if (!ok) bad.add(t.charAt(i));
        }
        StringBuilder sb = new StringBuilder();
        for (Character ch : bad) sb.append(ch);
        return sb.toString();
    }
}
