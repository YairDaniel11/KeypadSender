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

/**
 * שולח רצף הקשות בתוך שיחה קיימת (בלי לחייג).
 * אפליקציה שאינה החייגן ברירת המחדל לא יכולה לשלוח DTMF לשיחה פעילה דרך API ציבורי, ולכן:
 *  - עם רוט: מחזירים את מסך השיחה לחזית ומזריקים KEYCODE ספרות (`input keyevent`),
 *    שמסך השיחה מתרגם ל-DTMF.
 *  - בלי רוט: צלילי DTMF דרך הרמקול (ניסיוני - תלוי במכשיר ובביטול הד).
 */
public class InCallSendService extends Service {

    private static final String EXTRA_DIGITS = "digits";
    private static final int SWITCH_DELAY_MS = 1200; // המתנה אחרי חזרה למסך השיחה
    private static final int TONE_MS = 120;

    private final Handler main = new Handler(Looper.getMainLooper());

    public static void start(Context ctx, String digits) {
        Intent i = new Intent(ctx, InCallSendService.class);
        i.putExtra(EXTRA_DIGITS, digits);
        ctx.startService(i);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {
        final String digits = intent == null ? null : intent.getStringExtra(EXTRA_DIGITS);
        if (digits == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
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
                    ok = Prefs.useRoot(app) ? sendRoot(app, digits) : sendTones(app, digits);
                } catch (Exception ignored) {
                } finally {
                    if (wl != null && wl.isHeld()) wl.release();
                }
                toast(ok ? R.string.toast_sent : R.string.toast_send_failed);
                stopSelf(startId);
            }
        }, "incall-send").start();
        return START_NOT_STICKY;
    }

    private void toast(final int resId) {
        main.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static int keyCode(char c) {
        if (c >= '0' && c <= '9') return KeyEvent.KEYCODE_0 + (c - '0');
        if (c == '#') return KeyEvent.KEYCODE_POUND;
        if (c == '*') return KeyEvent.KEYCODE_STAR;
        return -1;
    }

    private boolean sendRoot(Context ctx, String digits) throws Exception {
        RootShell sh = RootShell.open();
        if (sh == null) return false;
        try {
            String cmd = Prefs.getInCallCmd(ctx).trim();
            if (cmd.length() > 0) {
                sh.run(cmd);
                Thread.sleep(SWITCH_DELAY_MS);
            }
            int gap = Math.max(0, Prefs.getGapMs(ctx));
            for (int i = 0; i < digits.length(); i++) {
                int code = keyCode(digits.charAt(i));
                if (code < 0) continue;
                if (sh.run("input keyevent " + code) == null) return false;
                Thread.sleep(gap);
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
