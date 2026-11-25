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
    private static final String EXTRA_CONFIRM = "confirm";
    private static final int CONFIRM_WAIT_MS = 600000; // המתנה מקסימלית לאישור (10 דקות)
    private static final int WAIT_CALL_MS = 40000;   // המתנה מקסימלית להתחלת השיחה
    private static final int WAIT_ANSWER_MS = 45000; // המתנה מקסימלית למענה
    private static final int ANSWER_SETTLE_MS = 1500; // מינימום המתנה אחרי המענה לפני המקש הראשון
    private static final int SWITCH_DELAY_MS = 1200; // המתנה אחרי חזרה למסך השיחה
    private static final int TONE_MS = 120;

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean aborted;      // השיחה הסתיימה באמצע השליחה
    private volatile String notFrontInfo;  // לא נמצא מסך שיחה בחזית (מה כן היה בחזית)
    private volatile boolean cancelled;    // המשתמש ביטל באישור "קו עם פרסומות"

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
        startScripted(ctx, parts, delaysMs, 0);
    }

    /** confirmAfter > 0 (קו עם פרסומות): אחרי כמה חלקים לעצור ולבקש אישור מהמשתמש. */
    public static void startScripted(Context ctx, String[] parts, long[] delaysMs, int confirmAfter) {
        Intent i = new Intent(ctx, InCallSendService.class);
        i.putExtra(EXTRA_PARTS, parts);
        i.putExtra(EXTRA_DELAYS, delaysMs);
        i.putExtra(EXTRA_WAIT, true);
        i.putExtra(EXTRA_CONFIRM, confirmAfter);
        ctx.startService(i);
    }

    /** כבר בשיחה עם הקו: שולח חלקים עם השהיות (ללא המתנה לחיבור/מענה). */
    public static void startParts(Context ctx, String[] parts, long[] delaysMs) {
        Intent i = new Intent(ctx, InCallSendService.class);
        i.putExtra(EXTRA_PARTS, parts);
        i.putExtra(EXTRA_DELAYS, delaysMs);
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
        final int confirmAfter = Math.max(0, intent.getIntExtra(EXTRA_CONFIRM, 0));
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wl = null;
                if (pm != null) {
                    wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KeypadSender::InCall");
                    wl.acquire(confirmAfter > 0 ? 180000 + CONFIRM_WAIT_MS : 180000);
                }
                boolean ok = false;
                try {
                    if (wait && !waitForCall(app)) {
                        toast(app.getString(R.string.toast_send_failed));
                        return;
                    }
                    if (Prefs.useRoot(app)) {
                        ok = sendRoot(app, parts, delays, wait, confirmAfter);
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
                else if (cancelled) toast(app.getString(R.string.toast_ad_cancelled));
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

    /** true = מסך השיחה בחזית (אחרי ניסיון להחזיר אותו). אחרת false ו-notFrontInfo מתעדכן. */
    private boolean ensureCallUi(Context ctx, RootShell sh) throws Exception {
        if (CallScreen.isCallUi(CallScreen.focusedWindow(sh))) return true;
        if (!inCall(ctx)) { aborted = true; return false; }
        if (CallScreen.bringToFront(ctx, sh)) return true;
        if (!inCall(ctx)) { aborted = true; return false; }
        String w = CallScreen.focusedWindow(sh);
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

    /**
     * קו עם פרסומות: פותח את מסך האישור (דרך רוט, כדי שיעלה גם מהרקע באנדרואיד חדש) וממתין.
     * true = אושר. false = בוטל / השיחה נותקה / נגמר הזמן.
     */
    private boolean awaitConfirm(Context ctx, RootShell sh) throws Exception {
        AdConfirmActivity.State.begin();
        try {
            if (sh.run("am start -n " + ctx.getPackageName() + "/.AdConfirmActivity") == null) return false;
            long end = System.currentTimeMillis() + CONFIRM_WAIT_MS;
            while (AdConfirmActivity.State.get() == AdConfirmActivity.State.PENDING) {
                if (!inCall(ctx)) { aborted = true; return false; }
                if (System.currentTimeMillis() > end) { cancelled = true; return false; }
                Thread.sleep(200);
            }
            if (AdConfirmActivity.State.get() == AdConfirmActivity.State.OK) {
                Thread.sleep(SWITCH_DELAY_MS); // המסך נסגר; ensureCallUi שאחריו מחזיר את מסך השיחה אם צריך
                return true;
            }
            cancelled = true;
            return false;
        } finally {
            // אם עדיין ממתין (יציאה בגלל ניתוק/זמן) - מסמנים כדי שהמסך ייסגר מעצמו
            AdConfirmActivity.State.answer(AdConfirmActivity.State.ABORT);
        }
    }

    private boolean sendRoot(Context ctx, String[] parts, long[] delays, boolean wait,
                             int confirmAfter) throws Exception {
        RootShell sh = RootShell.open();
        if (sh == null) return false;
        InjectorClient inj = null;
        try {
            if (!inCall(ctx)) { aborted = true; return false; }
            if (!ensureCallUi(ctx, sh)) return false;
            if (wait) {
                if (!waitAnswered(ctx, sh)) return false;
                // מיד אחרי המענה נתיב האודיו/ה-DTMF עוד לא מוכן, והמקש הראשון (השלוחה) אובד.
                // אם אין השהיה מוגדרת לפני החלק הראשון - ממתינים רגע קצר.
                long settle = ANSWER_SETTLE_MS - (delays.length > 0 ? delays[0] : 0);
                if (settle > 0 && !sleepInCall(ctx, settle)) { aborted = true; return false; }
            }
            int gap = Math.max(0, Prefs.getGapMs(ctx));
            int same = Prefs.getSameKeyGapMs(ctx);
            int batch = Math.max(1, Prefs.getBatch(ctx));
            // תהליך root קבוע שמזריק מקשים מהר ובלי לחיצה "ארוכה"; אם לא עלה - input keyevent
            inj = InjectorClient.open(ctx);
            // בלי תהליך קבוע: Android 7+ מקבל כמה קודים בפקודה אחת; לפני כן משרשרים ב-';'
            boolean multiArg = android.os.Build.VERSION.SDK_INT >= 24;
            for (int k = 0; k < parts.length; k++) {
                if (confirmAfter > 0 && k == confirmAfter && !awaitConfirm(ctx, sh)) return false;
                if (delays[k] > 0 && !sleepInCall(ctx, delays[k])) { aborted = true; return false; }
                java.util.List<Integer> list = new java.util.ArrayList<Integer>();
                for (int i = 0; i < parts[k].length(); i++) {
                    int code = keyCode(parts[k].charAt(i));
                    if (code >= 0) list.add(code);
                }
                // בדיקת מסך שיחה בחזית: בתחילת כל חלק ולפני קבוצה שיש בה # או *
                boolean first = true;
                int size = inj != null ? 12 : batch;
                for (int i = 0; i < list.size(); ) {
                    int end = i;
                    boolean special = false;
                    // קבוצה: עד size מקשים; # ו-* תמיד בקבוצה נפרדת משלהם
                    while (end < list.size() && end - i < size) {
                        int code = list.get(end);
                        boolean sp = code == KeyEvent.KEYCODE_POUND || code == KeyEvent.KEYCODE_STAR;
                        if (sp && end > i) break;
                        end++;
                        if (sp) { special = true; break; }
                    }
                    int[] codes = new int[end - i];
                    for (int j = i; j < end; j++) codes[j - i] = list.get(j);
                    i = end;
                    if (!inCall(ctx)) { aborted = true; return false; }
                    if ((first || special) && !ensureCallUi(ctx, sh)) return false;
                    first = false;
                    if (inj != null) {
                        if (!inj.send(gap, same, codes)) return false;
                    } else {
                        StringBuilder cmd = new StringBuilder();
                        for (int code : codes) {
                            if (multiArg) cmd.append(cmd.length() == 0 ? "input keyevent " : " ").append(code);
                            else cmd.append(cmd.length() == 0 ? "" : "; ").append("input keyevent ").append(code);
                        }
                        if (sh.run(cmd.toString()) == null) return false;
                    }
                    if (gap > 0 && i < list.size() && !sleepInCall(ctx, gap)) { aborted = true; return false; }
                }
            }
            return true;
        } finally {
            if (inj != null) inj.close();
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
            int same = Prefs.getSameKeyGapMs(ctx);
            char prev = 0;
            for (int i = 0; i < digits.length(); i++) {
                char c = digits.charAt(i);
                int tone;
                if (c >= '0' && c <= '9') tone = c - '0';          // TONE_DTMF_0..9
                else if (c == '#') tone = ToneGenerator.TONE_DTMF_P;
                else if (c == '*') tone = ToneGenerator.TONE_DTMF_S;
                else continue;
                if (!inCall(ctx)) { aborted = true; return false; }
                if (c == prev && same > 0) Thread.sleep(same);
                prev = c;
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
