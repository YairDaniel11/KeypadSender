package com.meir.keypadsender;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

/**
 * מצב "קו עם פרסומות": אחרי שנשלחה השלוחה, מסך זה שואל אם לשלוח את שאר ההקשות.
 * ההכרעה עוברת ל-InCallSendService דרך {@link State} (מצב משותף בתהליך).
 */
public class AdConfirmActivity extends Activity {

    /** מצב משותף בין השירות למסך. */
    static final class State {
        static final int IDLE = 0, PENDING = 1, OK = 2, CANCEL = 3, ABORT = 4;
        private static volatile int value = IDLE;
        static void begin() { value = PENDING; }
        static int get() { return value; }
        /** משנה רק אם עדיין ממתין (תשובה ראשונה קובעת). */
        static synchronized void answer(int v) { if (value == PENDING) value = v; }
        static void reset() { value = IDLE; }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable watch = new Runnable() {
        @Override public void run() {
            if (State.get() != State.PENDING) { finish(); return; } // השירות הפסיק (השיחה נותקה וכו')
            handler.postDelayed(this, 500);
        }
    };

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        // המסך עשוי להיות כבוי (טלפון ליד האוזן / נעול): להעיר אותו ולהציג מעל הנעילה
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_ad_confirm);
        if (State.get() != State.PENDING) { finish(); return; } // נפתח בלי בקשה פעילה

        Button ok = (Button) findViewById(R.id.btn_ad_ok);
        Button cancel = (Button) findViewById(R.id.btn_ad_cancel);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { State.answer(State.OK); finish(); }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { State.answer(State.CANCEL); finish(); }
        });
        ok.requestFocus();
        handler.postDelayed(watch, 500);
    }

    @Override
    public void onBackPressed() {
        State.answer(State.CANCEL);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(watch);
        // נסגר בלי תשובה (למשל נסגר מבחוץ): נחשב ביטול, כדי שהשירות לא ימתין לנצח
        if (isFinishing()) State.answer(State.CANCEL);
        super.onDestroy();
    }
}
