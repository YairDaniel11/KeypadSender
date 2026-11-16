package com.meir.keypadsender;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** חלון נפרד: כמה כפתורי שלוחה במסך הבית, ולכל אחד כותרת ושני מקשים. */
public class ExtSettingsActivity extends Activity {

    private EditText count;
    private LinearLayout rows;
    private final View[] rowViews = new View[Prefs.MAX_EXT];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ext_settings);

        count = (EditText) findViewById(R.id.edit_count);
        rows = (LinearLayout) findViewById(R.id.rows);
        LayoutInflater inf = LayoutInflater.from(this);
        for (int i = 1; i <= Prefs.MAX_EXT; i++) {
            View r = inf.inflate(R.layout.row_ext_config, rows, false);
            ((TextView) r.findViewById(R.id.row_title)).setText(getString(R.string.label_ext_row, i));
            ((EditText) r.findViewById(R.id.row_label)).setText(Prefs.getExtLabel(this, i));
            ((EditText) r.findViewById(R.id.row_number)).setText(Prefs.getExtNumber(this, i));
            ((EditText) r.findViewById(R.id.row_seq)).setText(Prefs.getExtSeq(this, i));
            ((EditText) r.findViewById(R.id.row_suffix)).setText(Prefs.getExtSuffix(this, i));
            rows.addView(r);
            rowViews[i - 1] = r;
        }
        count.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { showRows(); }
        });
        count.setText(String.valueOf(Prefs.getExtCount(this)));
        count.requestFocus();

        findViewById(R.id.btn_export).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { exportToClipboard(); }
        });
        findViewById(R.id.btn_import).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { importFromClipboard(); }
        });
        findViewById(R.id.btn_save_ext).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
    }

    private int currentCount() {
        try {
            int n = Integer.parseInt(count.getText().toString().trim());
            return Math.max(1, Math.min(Prefs.MAX_EXT, n));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private void showRows() {
        int n = currentCount();
        for (int i = 0; i < Prefs.MAX_EXT; i++) {
            rowViews[i].setVisibility(i < n ? View.VISIBLE : View.GONE);
        }
    }

    private static String text(View row, int id) {
        return ((EditText) row.findViewById(id)).getText().toString().trim();
    }

    private void save() {
        String[] labels = new String[Prefs.MAX_EXT];
        String[] numbers = new String[Prefs.MAX_EXT];
        String[] seqs = new String[Prefs.MAX_EXT];
        String[] suffixes = new String[Prefs.MAX_EXT];
        for (int i = 0; i < Prefs.MAX_EXT; i++) {
            labels[i] = text(rowViews[i], R.id.row_label);
            numbers[i] = text(rowViews[i], R.id.row_number);
            seqs[i] = text(rowViews[i], R.id.row_seq);
            suffixes[i] = text(rowViews[i], R.id.row_suffix);
        }
        Prefs.saveExtConfig(this, currentCount(), labels, numbers, seqs, suffixes);
        Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    @SuppressWarnings("deprecation")
    private void exportToClipboard() {
        try {
            android.text.ClipboardManager cm =
                    (android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setText(SettingsPorter.export(this));
            Toast.makeText(this, R.string.toast_exported, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_import_bad, Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressWarnings("deprecation")
    private void importFromClipboard() {
        try {
            android.text.ClipboardManager cm =
                    (android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            CharSequence t = cm.hasText() ? cm.getText() : null;
            if (t == null) throw new org.json.JSONException("empty");
            SettingsPorter.importFrom(this, t.toString());
            Toast.makeText(this, R.string.toast_imported, Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_import_bad, Toast.LENGTH_LONG).show();
        }
    }

    /** חזור בשדה טקסט = מחיקת תו; רק בשדה ריק יוצאים מהמסך. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (KeyUtil.backAsDelete(this, ev)) return true;
        return super.dispatchKeyEvent(ev);
    }
}
