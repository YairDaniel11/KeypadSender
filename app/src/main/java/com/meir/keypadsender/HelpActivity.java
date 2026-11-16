package com.meir.keypadsender;

import android.app.Activity;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * מסך עזרה: תוכן עניינים בראש (לחיצה על כותרת מגלגלת אליה), ואז כותרות + תוכן.
 * הסעיף האחרון הוא מפת ההקשות בפועל. החצים מגלגלים/מנווטים.
 */
public class HelpActivity extends Activity {

    private static final int[] TITLES = {R.string.help_t1, R.string.help_t2, R.string.help_t3,
            R.string.help_t4, R.string.help_t5, R.string.help_t6, R.string.help_t7, R.string.help_t8,
            R.string.help_t9};
    private static final int[] BODIES = {R.string.help_b1, R.string.help_b2, R.string.help_b3,
            R.string.help_b4, R.string.help_b5, R.string.help_b6, R.string.help_b7, R.string.help_b8};

    private ScrollView scroll;
    private LinearLayout box;
    private float d;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        scroll = (ScrollView) findViewById(R.id.help_scroll);
        box = (LinearLayout) findViewById(R.id.help_content);
        d = getResources().getDisplayMetrics().density;
        int accent = ThemeUtil.resolveColor(this, R.attr.colorAccent);
        int primary = ThemeUtil.resolveColor(this, R.attr.colorTextPrimary);

        // תוכן עניינים
        TextView toc = text(getString(R.string.help_toc), 15, primary, false);
        toc.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(toc, lp(0));
        final TextView[] heads = new TextView[TITLES.length];
        final TextView[] links = new TextView[TITLES.length];
        for (int i = 0; i < TITLES.length; i++) {
            links[i] = link(getString(TITLES[i]), 18, accent);
            box.addView(links[i], lp(2));
        }

        // הסעיפים
        for (int i = 0; i < TITLES.length; i++) {
            TextView t = text(getString(TITLES[i]), 20, accent, false);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            heads[i] = t;
            box.addView(t, lp(26));

            String body = i < BODIES.length ? getString(BODIES[i])
                    : getString(R.string.help_keymap_intro)
                            + KeypadEncoder.describeTable(null)
                            + getString(R.string.help_keymap_extra);
            box.addView(text(body, 17, primary, true), lp(6));

            TextView back = link(getString(R.string.help_back_top), 15, accent);
            box.addView(back, lp(10));
            back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { scrollTo(0); }
            });
        }
        for (int i = 0; i < links.length; i++) {
            final int idx = i;
            links[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { scrollTo(heads[idx].getTop()); }
            });
        }
        links[0].requestFocus();
    }

    private void scrollTo(final int y) {
        scroll.post(new Runnable() {
            @Override public void run() { scroll.smoothScrollTo(0, y); }
        });
    }

    private LinearLayout.LayoutParams lp(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = (int) (topDp * d);
        return p;
    }

    private TextView text(String s, int sp, int color, boolean spaced) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (spaced) t.setLineSpacing(0, 1.2f);
        return t;
    }

    /** קישור: מסומן בקו תחתון, ניתן לפוקוס ולהפעלה (גם במקשים). */
    private TextView link(String s, int sp, int color) {
        TextView t = text(s, sp, color, false);
        t.setPaintFlags(t.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        t.setFocusable(true);
        t.setClickable(true);
        t.setPadding((int) (6 * d), (int) (8 * d), (int) (6 * d), (int) (8 * d));
        t.setBackgroundResource(android.R.drawable.list_selector_background);
        return t;
    }
}
