package com.meir.keypadsender;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** מסך עזרה: כותרות + תוכן, בגלילה (החצים מגלגלים). */
public class HelpActivity extends Activity {

    private static final int[] TITLES = {R.string.help_t1, R.string.help_t2, R.string.help_t3,
            R.string.help_t4, R.string.help_t5, R.string.help_t6, R.string.help_t7, R.string.help_t8};
    private static final int[] BODIES = {R.string.help_b1, R.string.help_b2, R.string.help_b3,
            R.string.help_b4, R.string.help_b5, R.string.help_b6, R.string.help_b7, R.string.help_b8};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        LinearLayout box = (LinearLayout) findViewById(R.id.help_content);
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < TITLES.length; i++) {
            TextView t = new TextView(this);
            t.setText(TITLES[i]);
            t.setTextSize(20);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextColor(ThemeUtil.resolveColor(this, R.attr.colorAccent));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (i == 0 ? 0 : 22 * d);
            box.addView(t, lp);

            TextView b = new TextView(this);
            b.setText(BODIES[i]);
            b.setTextSize(17);
            b.setLineSpacing(0, 1.2f);
            b.setTextColor(ThemeUtil.resolveColor(this, R.attr.colorTextPrimary));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.topMargin = (int) (6 * d);
            box.addView(b, bp);
        }
        findViewById(R.id.help_scroll).requestFocus();
    }
}
