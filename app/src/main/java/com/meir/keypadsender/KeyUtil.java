package com.meir.keypadsender;

import android.app.Activity;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

/** מקש חזור בשדה טקסט במכשיר מקשים: מוחק תו (או את הבחירה); רק בשדה ריק הוא יוצא כרגיל. */
public final class KeyUtil {
    private KeyUtil() {}

    /** לקרוא מ-dispatchKeyEvent. true = האירוע טופל (אין יציאה מהמסך). */
    public static boolean backAsDelete(Activity a, KeyEvent ev) {
        if (ev.getKeyCode() != KeyEvent.KEYCODE_BACK) return false;
        View f = a.getCurrentFocus();
        if (!(f instanceof EditText)) return false;
        EditText et = (EditText) f;
        if (et.length() == 0) return false; // אין טקסט כלל: חזור רגיל
        if (ev.getAction() == KeyEvent.ACTION_DOWN) {
            Editable e = et.getText();
            int s = et.getSelectionStart();
            int en = et.getSelectionEnd();
            if (s < 0 || en < 0) { s = en = e.length(); }
            int lo = Math.min(s, en);
            int hi = Math.max(s, en);
            if (hi > lo) e.delete(lo, hi);
            else if (lo > 0) e.delete(lo - 1, lo);
        }
        return true; // גם ACTION_UP נבלע, כדי שלא יפעיל יציאה
    }
}
