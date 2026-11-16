package com.meir.keypadsender;

/** נרמול והשוואת מספרי טלפון (טהור, בלי תלות באנדרואיד). */
public final class PhoneNumberUtil {
    private PhoneNumberUtil() {}

    /** חותך אחרי פסיק/נקודה-פסיק (ספרות שנשלחות אחרי החיוג), משאיר ספרות בלבד, וממיר קידומת ישראל ל-0. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        int cut = raw.length();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ',' || c == ';') { cut = i; break; }
        }
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < cut; i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') d.append(c);
        }
        String digits = d.toString();
        if (digits.startsWith("00972")) return "0" + digits.substring(5);
        if (digits.startsWith("972")) return "0" + digits.substring(3);
        return digits;
    }

    public static boolean same(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        if (x.length() == 0 || y.length() == 0) return false;
        if (x.equals(y)) return true;
        if (x.length() >= 9 && y.length() >= 9) {
            return x.substring(x.length() - 9).equals(y.substring(y.length() - 9));
        }
        return false;
    }
}
