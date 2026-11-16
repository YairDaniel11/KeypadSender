package com.meir.keypadsender;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private EditText number, p1, p2, p3, gap, cmd, batch;
    private CheckBox root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        number = (EditText) findViewById(R.id.edit_number);
        p1 = (EditText) findViewById(R.id.edit_p1);
        p2 = (EditText) findViewById(R.id.edit_p2);
        p3 = (EditText) findViewById(R.id.edit_p3);
        gap = (EditText) findViewById(R.id.edit_gap);
        cmd = (EditText) findViewById(R.id.edit_cmd);
        root = (CheckBox) findViewById(R.id.cb_root);

        number.setText(Prefs.getNumber(this));
        p1.setText(String.valueOf(Prefs.getP1(this)));
        p2.setText(String.valueOf(Prefs.getP2(this)));
        p3.setText(String.valueOf(Prefs.getP3(this)));
        gap.setText(String.valueOf(Prefs.getGapMs(this)));
        batch = (EditText) findViewById(R.id.edit_batch);
        batch.setText(String.valueOf(Prefs.getBatch(this)));
        cmd.setText(Prefs.getInCallCmd(this));
        root.setChecked(Prefs.useRoot(this));

        // פוקוס התחלתי: שדה המספר (הדבר הראשון שצריך למלא)
        number.requestFocus();

        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        findViewById(R.id.btn_test_root).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { testRoot(); }
        });
    }

    /** חזור בשדה טקסט = מחיקת תו; רק בשדה ריק יוצאים מהמסך. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (KeyUtil.backAsDelete(this, ev)) return true;
        return super.dispatchKeyEvent(ev);
    }

    private static int num(EditText e, int def) {
        try { return Integer.parseInt(e.getText().toString().trim()); }
        catch (NumberFormatException ex) { return def; }
    }

    private void save() {
        Prefs.saveSettings(this,
                number.getText().toString().trim(),
                num(p1, Prefs.DEFAULT_P1), num(p2, Prefs.DEFAULT_P2), num(p3, Prefs.DEFAULT_P3),
                root.isChecked(),
                num(gap, Prefs.DEFAULT_GAP_MS),
                cmd.getText().toString().trim());
        Prefs.setBatch(this, num(batch, Prefs.DEFAULT_BATCH));
        Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void testRoot() {
        new Thread(new Runnable() {
            @Override public void run() {
                RootShell sh = RootShell.open();
                final boolean ok = sh != null;
                if (sh != null) sh.close();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(SettingsActivity.this,
                                ok ? R.string.toast_root_ok : R.string.toast_root_fail,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, "root-test").start();
    }
}
