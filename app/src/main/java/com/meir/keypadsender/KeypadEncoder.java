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
        String t = normalizeText(text);
        StringBuilder out = new StringBuilder();
        char lastKey = 0;

        for (int i = 0; i < t.length(); i++) {
            char c = normalizeFinal(t.charAt(i));
            char key;
            int presses;

            if (c == ' ') {
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
        return out.append(END).toString();
    }

    /** תווים שהמרה תדלג עליהם (ללא כפילויות), לתצוגת אזהרה. */
    public static String findUnsupported(String text) {
        String t = normalizeText(text);
        Set<Character> bad = new LinkedHashSet<Character>();
        for (int i = 0; i < t.length(); i++) {
            char c = normalizeFinal(t.charAt(i));
            boolean ok = c == ' ' || (c >= '0' && c <= '9') || isMappedLetter(c);
            if (!ok) bad.add(t.charAt(i));
        }
        StringBuilder sb = new StringBuilder();
        for (Character ch : bad) sb.append(ch);
        return sb.toString();
    }
}
