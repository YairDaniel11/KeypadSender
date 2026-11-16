package com.meir.keypadsender;

import android.content.Context;
import android.os.Build;
import android.telecom.TelecomManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** מעביר את מסך השיחה הפעילה לחזית. */
final class CallScreen {
    private CallScreen() {}

    private static final long SWITCH_WAIT_MS = 1200;

    /** מה נוסה ומה היה בחזית (לאבחון, מוצג בהודעה). */
    static volatile String lastInfo = "";

    /** דרך TelecomManager (API 23+, בלי רוט). true = הבקשה נשלחה (לא מבטיח שהמסך עלה). */
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

    /** החלון שבפוקוס כרגע, למשל "com.android.phone/com.android.phone.InCallScreen" (או ריק). */
    static String focusedWindow(RootShell sh) throws IOException {
        String out = sh.run("dumpsys window windows | grep mCurrentFocus");
        if (out == null) return "";
        int i = out.indexOf("mCurrentFocus");
        if (i < 0) return "";
        String line = out.substring(i);
        int nl = line.indexOf('\n');
        if (nl >= 0) line = line.substring(0, nl);
        int sp = line.lastIndexOf(' ');
        String w = sp >= 0 ? line.substring(sp + 1) : line;
        return w.replace("}", "").trim();
    }

    static boolean isCallUi(String w) {
        String l = w.toLowerCase();
        if (l.contains("keypadsender")) return false;
        // מקלדת החיוג (Dialtacts/Dialpad) היא לא מסך השיחה
        if (l.contains("dialtacts") || l.contains("dialpad") || l.contains("contacts")) return false;
        return l.contains("phone") || l.contains("dialer") || l.contains("incall")
                || l.contains("telecom") || l.contains("call");
    }

    private static final Pattern COMPONENT = Pattern.compile("([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)");

    /** מחפש במערכת אקטיביטי של מסך שיחה שכבר קיים (הכי אמין: זה מה שהמכשיר באמת משתמש בו). */
    private static List<String> discoverCommands(RootShell sh) throws IOException {
        List<String> cmds = new ArrayList<String>();
        String out = sh.run("dumpsys activity activities | grep -i -E 'incall|InCallScreen|calling'");
        if (out == null) return cmds;
        for (String line : out.split("\n")) {
            Matcher m = COMPONENT.matcher(line);
            while (m.find()) {
                String comp = m.group(1);
                String l = comp.toLowerCase();
                if (l.contains("keypadsender")) continue;
                if (!(l.contains("incall") || l.contains("call"))) continue;
                if (l.contains("dialtacts") || l.contains("dialpad")) continue;
                String c = "am start -n " + comp;
                if (!cmds.contains(c)) cmds.add(c);
            }
        }
        return cmds;
    }

    private static boolean isCallComponent(android.content.ComponentName c) {
        if (c == null) return false;
        String s = c.flattenToString().toLowerCase();
        if (s.contains("keypadsender") || s.contains("dialtacts")) return false;
        return s.contains("incall") || s.contains("com.android.phone")
                || s.contains("dialer") || s.contains("telecom");
    }

    @SuppressWarnings("deprecation")
    private static List<android.app.ActivityManager.RunningTaskInfo> tasks(Context ctx) {
        try {
            android.app.ActivityManager am =
                    (android.app.ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            return am.getRunningTasks(50);
        } catch (Throwable e) {
            return new ArrayList<android.app.ActivityManager.RunningTaskInfo>();
        }
    }

    /** כל המשימות הרצות (לאבחון). */
    static String taskDump(Context ctx) {
        StringBuilder sb = new StringBuilder();
        for (android.app.ActivityManager.RunningTaskInfo t : tasks(ctx)) {
            sb.append("#").append(t.id).append(" base=")
                    .append(t.baseActivity == null ? "-" : t.baseActivity.flattenToShortString())
                    .append(" top=")
                    .append(t.topActivity == null ? "-" : t.topActivity.flattenToShortString())
                    .append(" | ");
        }
        return sb.toString();
    }

    /** מעביר לחזית את משימת מסך השיחה (בלי רוט, API עד 20 רואה את כל המשימות). */
    @SuppressWarnings("deprecation")
    static boolean moveCallTaskToFront(Context ctx) {
        try {
            android.app.ActivityManager.RunningTaskInfo best = null;
            for (android.app.ActivityManager.RunningTaskInfo t : tasks(ctx)) {
                if (isCallComponent(t.topActivity) || isCallComponent(t.baseActivity)) {
                    boolean topIn = t.topActivity != null
                            && t.topActivity.flattenToString().toLowerCase().contains("incall");
                    if (best == null || topIn) best = t;
                    if (topIn) break;
                }
            }
            if (best == null) return false;
            android.app.ActivityManager am =
                    (android.app.ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            am.moveTaskToFront(best.id, 0);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static String oneLine(RootShell sh, String cmd) throws IOException {
        String o = sh.run(cmd);
        if (o == null) return "";
        o = o.trim().replace("\n", " | ");
        return o.length() > 400 ? o.substring(0, 400) : o;
    }

    /**
     * מנסה להעלות את מסך השיחה לחזית ומוודא (לפי חלון הפוקוס). חוסם (להריץ בת'רד רקע).
     * true = מסך השיחה בחזית.
     */
    static boolean bringToFront(Context ctx, RootShell sh) throws Exception {
        String w = focusedWindow(sh);
        lastInfo = "התחלה: " + w;
        if (isCallUi(w)) return true;
        // הכי אמין: להעביר את המשימה של מסך השיחה לחזית (בלי intent שמסך השיחה עלול לסגור)
        if (moveCallTaskToFront(ctx)) {
            Thread.sleep(SWITCH_WAIT_MS);
            String mw = focusedWindow(sh);
            lastInfo += "\nmoveTaskToFront => " + mw;
            if (isCallUi(mw)) return true;
        }
        List<String> cmds = new ArrayList<String>();
        // קודם מה שהמערכת עצמה מכירה כמסך שיחה (לא מקלדת חיוג)
        for (String c : discoverCommands(sh)) if (!cmds.contains(c)) cmds.add(c);
        String user = Prefs.getInCallCmd(ctx).trim();
        if (user.length() > 0 && !cmds.contains(user)) cmds.add(user);
        for (String c : FALLBACK_CMDS) if (!cmds.contains(c)) cmds.add(c);
        for (String c : cmds) {
            sh.run(c);
            Thread.sleep(SWITCH_WAIT_MS);
            String fw = focusedWindow(sh);
            lastInfo += "\n" + c.replace("am start -n ", "") + " => " + fw;
            if (isCallUi(fw)) return true;
        }
        // אחרון: TelecomManager (במכשירים מסוימים הוא פותח את מקלדת החיוג)
        if (showViaTelecom(ctx)) {
            Thread.sleep(SWITCH_WAIT_MS);
            String fw = focusedWindow(sh);
            lastInfo += "\ntelecom => " + fw;
            if (isCallUi(fw)) return true;
        }
        return false;
    }
}
