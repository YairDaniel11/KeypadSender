package com.meir.keypadsender;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** היסטוריית הקלדה: אישור = טוען את הטקסט לחלונית, מקש מחיקה / לחיצה ארוכה = מוחק פריט. */
public class HistoryActivity extends Activity {
    public static final String EXTRA_TEXT = "text";

    private ListView list;
    private TextView empty;
    private List<String> items;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        list = (ListView) findViewById(R.id.list);
        empty = (TextView) findViewById(R.id.empty);
        reload();

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos < 0 || pos >= items.size()) return;
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_TEXT, items.get(pos)));
                finish();
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                delete(pos);
                return true;
            }
        });
        findViewById(R.id.btn_clear_history).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new android.app.AlertDialog.Builder(HistoryActivity.this)
                        .setMessage(R.string.confirm_clear_history)
                        .setPositiveButton(R.string.yes, new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int w) {
                                Prefs.clearHistory(HistoryActivity.this);
                                reload();
                            }
                        })
                        .setNegativeButton(R.string.no, null)
                        .show();
            }
        });
        list.requestFocus();
    }

    private void reload() {
        items = Prefs.getHistory(this);
        list.setAdapter(new ArrayAdapter<String>(this, R.layout.row_history, items));
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void delete(int pos) {
        if (pos < 0 || pos >= items.size()) return;
        Prefs.removeHistory(this, pos);
        reload();
        if (!items.isEmpty()) list.setSelection(Math.min(pos, items.size() - 1));
        Toast.makeText(this, R.string.toast_deleted, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (ev.getKeyCode() == KeyEvent.KEYCODE_DEL && getCurrentFocus() == list) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
                delete(list.getSelectedItemPosition());
            }
            return true;
        }
        return super.dispatchKeyEvent(ev);
    }
}
