package com.meir.keypadsender;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.widget.Toast;

import java.io.IOException;

/**
 * שולח רצף הקשות בתוך שיחה (בלי לחייג).
 * אפליקציה שאינה החייגן ברירת המחדל לא יכולה לשלוח DTMF לשיחה פעילה דרך API ציבורי, ולכן:
 *  - עם רוט: מוודאים שמסך השיחה בחזית (ואם לא - מחזירים אותו), ומזריקים KEYCODE ספרות
 *    (`input keyevent`) שמסך השיחה מתרגם ל-DTMF. אם מסך השיחה לא בחזית - לא מזריקים כלל,
 *    כדי שהמקשים (כולל #) לא ייכנסו לאפליקציה אחרת או לשולחן העבודה.
 *  - בלי רוט: צלילי DTMF דרך הרמקול (ניסיוני - תלוי במכשיר ובביטול הד).
 */
public class InCallSendService extends Service {

    private static final String EXTRA_DIGITS = "digits";
    private static final String EXTRA_PARTS = "parts";
    private static final String EXTRA_DELAYS = "delays";
    private static final String EXTRA_WAIT = "wait";
    private static final int WAIT_CALL_MS = 40000;   // המתנה מקסימלית להתחלת השיחה
    private static final int WAIT_ANSWER_MS = 45000; // המתנה מקסימלית למענה
    private static final int SWITCH_DELAY_MS = 1200; // המתנה אחרי חזרה למסך השיחה
    private static final int TONE_MS = 120;

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean aborted;      // השיחה הסתיימה באמצע השליחה
    private volatile String notFrontInfo;  // לא נמצא מסך שיחה בחזית (מה כן היה בחזית)

    public static void start(Context ctx, String digits) {
        Intent i = new Intent(ctx, InCallSendService.class);
        i.putExtra(EXTRA_DIGITS, digits);
        ctx.startService(i);
    }

    /**
     * אחרי חיוג של המספר בלבד: ממתין שהשיחה תתחבר ושתיענה, ואז שולח כל חלק (שלוחה, 2, ההקשות)
     * אחרי ההשהיה שלו במילישניות. דורש רוט.
     */
    public static void startScripted(Context ctx, String[] parts, long[] delaysMs) {
        Intent i = new Intent(ctx, InCallSendService.class);
        i.putExtra(EXTRA_PARTS, parts);
        i.putExtra(EXTRA_DELAYS, delaysMs);
        i.putExtra(EXTRA_WAIT, true);
        ctx.startService(i);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {
        String d = intent == null ? null : intent.getStringExtra(EXTRA_DIGITS);
        String[] p = intent == null ? null : intent.getStringArrayExtra(EXTRA_PARTS);
        long[] dl = intent == null ? null : intent.getLongArrayExtra(EXTRA_DELAYS);
        if (p == null && d != null) { p = new String[]{d}; dl = new long[]{0}; }
        if (p == null || dl == null || dl.length != p.length) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        final String[] parts = p;
        final long[] delays = dl;
        final boolean wait = intent.getBooleanExtra(EXTRA_WAIT, false);
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wl = null;
                if (pm != null) {
                    wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KeypadSender::InCall");
                    wl.acquire(180000);
                }
                boolean ok = false;
                try {
                    if (wait && !waitForCall(app)) {
                        toast(app.getString(R.string.toast_send_failed));
                        return;
                    }
                    if (Prefs.useRoot(app)) {
                        ok = sendRoot(app, parts, delays, wait);
                    } else {
                        StringBuilder all = new StringBuilder();
                        for (String part : parts) all.append(part);
                        ok = sendTones(app, all.toString());
                    }
                } catch (Exception ignored) {
                } finally {
                    if (wl != null && wl.isHeld()) wl.release();
                    stopSelf(startId);
                }
                if (ok) toast(app.getString(R.string.toast_sent));
                else if (notFrontInfo != null) {
                    toast(app.getString(R.string.toast_not_front, notFrontInfo));
                } else toast(app.getString(aborted ? R.string.toast_aborted : R.string.toast_send_failed));
            }
        }, "incall-send").start();
        return START_NOT_STICKY;
    }

    private void toast(final String text) {
        main.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static int keyCode(char c) {
        if (c >= '0' && c <= '9') return KeyEvent.KEYCODE_0 + (c - '0');
        if (c == '#') return KeyEvent.KEYCODE_POUND;
        if (c == '*') return KeyEvent.KEYCODE_STAR;
        return -1;
    }

    /** השיחה עם הקו עדיין פעילה? (אחרת אסור להזריק מקשים: הם היו נכנסים לאפליקציה אחרת). */
    private static boolean inCall(Context ctx) {
        return CallTracker.status(ctx) == CallTracker.Status.IN_CALL_TARGET;
    }

    /** ישן במקטעים קצרים; false = השיחה הסתיימה בינתיים. */
    private static boolean sleepInCall(Context ctx, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (true) {
            if (!inCall(ctx)) return false;
            long left = end - System.currentTimeMillis();
            if (left <= 0) return true;
            Thread.sleep(Math.min(100, left));
        }
    }

    private static boolean waitForCall(Context ctx) throws InterruptedException {
        long end = System.currentTimeMillis() + WAIT_CALL_MS;
        while (System.currentTimeMillis() < end) {
            if (inCall(ctx)) return true;
            Thread.sleep(500);
        }
        return false;
    }

    // ---- ווידוא שמסך השיחה בחזית (רוט) ----

    /** החלון שבפוקוס כרגע, למשל "com.android.phone/com.android.phone.InCallScreen" (או ריק). */
    private static String focusedWindow(RootShell sh) throws IOException {
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

    private static boolean isCallUi(String w) {
        String l = w.toLowerCase();
        if (l.contains("keypadsender")) return false;
        return l.contains("phone") || l.contains("dialer") || l.contains("incall")
                || l.contains("telecom") || l.contains("call");
    }

    /** true = מסך השיחה בחזית (אחרי ניסיון להחזיר אותו). אחרת false ו-notFrontInfo מתעדכן. */
    private boolean ensureCallUi(Context ctx, RootShell sh) throws Exception {
        String w = focusedWindow(sh);
        if (isCallUi(w)) return true;
        if (!inCall(ctx)) { aborted = true; return false; }
        // 1) בלי רוט: TelecomManager מעלה את מסך השיחה
        if (CallScreen.showViaTelecom(ctx)) {
            if (!sleepInCall(ctx, SWITCH_DELAY_MS)) { aborted = true; return false; }
            w = focusedWindow(sh);
            if (isCallUi(w)) return true;
        }
        // 2) הפקודה שהוגדרה, ואז פקודות גיבוי
        java.util.List<String> cmds = new java.util.ArrayList<String>();
        String cmd = Prefs.getInCallCmd(ctx).trim();
        if (cmd.length() > 0) cmds.add(cmd);
        for (String f : CallScreen.FALLBACK_CMDS) if (!cmds.contains(f)) cmds.add(f);
        for (String c : cmds) {
            if (!inCall(ctx)) { aborted = true; return false; }
            sh.run(c);
            if (!sleepInCall(ctx, SWITCH_DELAY_MS)) { aborted = true; return false; }
            w = focusedWindow(sh);
            if (isCallUi(w)) return true;
        }
        notFrontInfo = w.length() == 0 ? "?" : w;
        return false;
    }

    // ---- המתנה למענה (רוט, best-effort) ----

    /** 0 = לא ידוע, 1 = מחייג/מצלצל, 2 = השיחה פעילה (נענתה). */
    private static int callPhase(RootShell sh) throws IOException {
        String out = sh.run("dumpsys phone | grep -i foregroundcall");
        if (out == null) return 0;
        String u = out.toUpperCase();
        if (u.contains("ACTIVE")) return 2;
        if (u.contains("DIALING") || u.contains("ALERTING")) return 1;
        return 0;
    }

    /**
     * ממתין שהשיחה תיענה. אם המכשיר לא מדווח מצב כזה (לא ידוע) - ממשיך מיד, וההמתנה נשארת
     * ההשהיה שהוגדרה (p1). false = השיחה הסתיימה / לא נענתה בזמן.
     */
    private boolean waitAnswered(Context ctx, RootShell sh) throws Exception {
        long end = System.currentTimeMillis() + WAIT_ANSWER_MS;
        int unknown = 0;
        boolean seenPending = false;
        while (System.currentTimeMillis() < end) {
            if (!inCall(ctx)) { aborted = true; return false; }
            int ph = callPhase(sh);
            if (ph == 2) return true;
            if (ph == 1) seenPending = true;
            else if (!seenPending && ++unknown >= 3) return true; // אין דיווח מצב במכשיר הזה
            if (!sleepInCall(ctx, 600)) { aborted = true; return false; }
        }
        return false;
    }

    private boolean sendRoot(Context ctx, String[] parts, long[] delays, boolean wait) throws Exception {
        RootShell sh = RootShell.open();
        if (sh == null) return false;
        try {
            if (!inCall(ctx)) { aborted = true; return false; }
            if (!ensureCallUi(ctx, sh)) return false;
            if (wait && !waitAnswered(ctx, sh)) return false;
            int gap = Math.max(0, Prefs.getGapMs(ctx));
            int batch = Math.max(1, Prefs.getBatch(ctx));
            // Android 7+: input keyevent מקבל כמה קודים בפקודה אחת; לפני כן משרשרים ב-';'
            boolean multiArg = android.os.Build.VERSION.SDK_INT >= 24;
            for (int k = 0; k < parts.length; k++) {
                if (delays[k] > 0 && !sleepInCall(ctx, delays[k])) { aborted = true; return false; }
                StringBuilder codes = new StringBuilder();
                java.util.List<Integer> list = new java.util.ArrayList<Integer>();
                for (int i = 0; i < parts[k].length(); i++) {
                    int code = keyCode(parts[k].charAt(i));
                    if (code >= 0) list.add(code);
                }
                // בדיקת מסך שיחה בחזית: בתחילת כל חלק ולפני קבוצה שיש בה # או *
                boolean first = true;
                for (int i = 0; i < list.size(); i += batch) {
                    int end = Math.min(list.size(), i + batch);
                    boolean special = false;
                    codes.setLength(0);
                    for (int j = i; j < end; j++) {
                        int code = list.get(j);
                        if (code == KeyEvent.KEYCODE_POUND || code == KeyEvent.KEYCODE_STAR) special = true;
                        if (multiArg) {
                            codes.append(codes.length() == 0 ? "input keyevent " : " ").append(code);
                        } else {
                            if (codes.length() > 0) codes.append("; ");
                            codes.append("input keyevent ").append(code);
                        }
                    }
                    if (!inCall(ctx)) { aborted = true; return false; }
                    if ((first || special) && !ensureCallUi(ctx, sh)) return false;
                    first = false;
                    if (sh.run(codes.toString()) == null) return false;
                    if (gap > 0 && !sleepInCall(ctx, gap)) { aborted = true; return false; }
                }
            }
            return true;
        } finally {
            sh.close();
        }
    }

    private boolean sendTones(Context ctx, String digits) throws Exception {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        boolean wasSpeaker = am != null && am.isSpeakerphoneOn();
        if (am != null) am.setSpeakerphoneOn(true);
        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_DTMF, 100);
        try {
            int gap = Math.max(0, Prefs.getGapMs(ctx));
            for (int i = 0; i < digits.length(); i++) {
                char c = digits.charAt(i);
                int tone;
                if (c >= '0' && c <= '9') tone = c - '0';          // TONE_DTMF_0..9
                else if (c == '#') tone = ToneGenerator.TONE_DTMF_P;
                else if (c == '*') tone = ToneGenerator.TONE_DTMF_S;
                else continue;
                if (!inCall(ctx)) { aborted = true; return false; }
                tg.startTone(tone, TONE_MS);
                Thread.sleep(TONE_MS + gap);
            }
            return true;
        } finally {
            tg.release();
            if (am != null) am.setSpeakerphoneOn(wasSpeaker);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
