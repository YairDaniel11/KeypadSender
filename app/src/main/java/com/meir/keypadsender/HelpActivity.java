package com.meir.keypadsender;

import android.app.Activity;
import android.os.Bundle;

/** מסך עזרה: טקסט גלילה (החצים מגלגלים). */
public class HelpActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        findViewById(R.id.help_scroll).requestFocus();
    }
}
