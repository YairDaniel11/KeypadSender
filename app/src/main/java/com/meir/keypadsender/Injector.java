package com.meir.keypadsender;

import android.os.IBinder;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

/**
 * תהליך root קבוע (app_process) שמזריק מקשים. במקום להפעיל `input keyevent` (JVM חדש לכל מקש, איטי מאוד)
 * התהליך נשאר פתוח וקורא פקודות מ-stdin: "gapMs code code code ...".
 * ההזרקה אסינכרונית וה-UP נשלח מיד אחרי ה-DOWN, כדי שהמערכת לא תפרש מקש (במיוחד #) כלחיצה ארוכה.
 */
public final class Injector {
    private static final int HOLD_MS = 90;     // DTMF: צליל קצר מדי לא מזוהה בקו
    private static final int MIN_GAP_MS = 70; // הפסקה מינימלית בין צלילים

    public static void main(String[] args) {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            IBinder b = (IBinder) sm.getMethod("getService", String.class).invoke(null, "input");
            Class<?> stub = Class.forName("android.hardware.input.IInputManager$Stub");
            Object im = stub.getMethod("asInterface", IBinder.class).invoke(null, b);
            Method inject = null;
            for (Method m : im.getClass().getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals("injectInputEvent") && p.length == 2) { inject = m; break; }
            }
            if (inject == null) { System.out.println("FAIL"); System.out.flush(); return; }
            System.out.println("READY");
            System.out.flush();

            BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
            String line;
            int last = -1;
            while ((line = r.readLine()) != null) {
                String[] t = line.trim().split("\\s+");
                if (t.length == 0 || t[0].length() == 0) continue;
                boolean ok = true;
                // "gap" או "gap:extra" - extra = המתנה נוספת לפני מקש זהה לקודם (הקשות חוזרות על אותו מקש)
                String[] g = t[0].split(":");
                long gap = Math.max(MIN_GAP_MS, Long.parseLong(g[0]));
                long extra = g.length > 1 ? Math.max(0, Long.parseLong(g[1])) : 0;
                for (int i = 1; i < t.length; i++) {
                    int code = Integer.parseInt(t[i]);
                    if (code == last && extra > 0) Thread.sleep(extra);
                    last = code;
                    long now = SystemClock.uptimeMillis();
                    ok &= send(im, inject, new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, 0, -1, 0,
                            0, InputDevice.SOURCE_KEYBOARD));
                    Thread.sleep(HOLD_MS);
                    long up = SystemClock.uptimeMillis();
                    ok &= send(im, inject, new KeyEvent(now, up, KeyEvent.ACTION_UP, code, 0, 0, -1, 0,
                            0, InputDevice.SOURCE_KEYBOARD));
                    Thread.sleep(gap);
                }
                System.out.println(ok ? "OK" : "ERR");
                System.out.flush();
            }
        } catch (Throwable e) {
            System.out.println("FAIL");
            System.out.flush();
        }
    }

    private static boolean send(Object im, Method m, KeyEvent ev) throws Exception {
        Object r = m.invoke(im, ev, 0); // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
        return !(r instanceof Boolean) || (Boolean) r;
    }
}
