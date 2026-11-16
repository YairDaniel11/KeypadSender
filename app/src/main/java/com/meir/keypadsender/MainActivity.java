package com.meir.keypadsender;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private EditText input;
    private TextView preview;
    private TextView warn;
    private TextView status;
    private RadioGroup extGroup;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() { refreshStatus(); }
    };
    private PhoneStateListener listener;
    private boolean pendingSend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        input = (EditText) findViewById(R.id.input);
        preview = (TextView) findViewById(R.id.preview);
        warn = (TextView) findViewById(R.id.warn);
        status = (TextView) findViewById(R.id.status);
        extGroup = (RadioGroup) findViewById(R.id.ext_group);
        Button send = (Button) findViewById(R.id.btn_send);
        Button settings = (Button) findViewById(R.id.btn_settings);

        int ext = Prefs.getExt(this);
        ((RadioButton) findViewById(ext == 3 ? R.id.rb_ext3 : ext == 2 ? R.id.rb_ext2 : R.id.rb_ext1))
                .setChecked(true);
        extGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                Prefs.setExt(MainActivity.this, selectedExt());
                refreshStatus();
            }
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updatePreview(s.toString()); }
        });
        updatePreview("");

        send.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { trySend(); }
        });
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        // פוקוס התחלתי: שדה הטקסט (הפעולה הנפוצה); השלוחה זמינה בחץ למעלה
        input.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        startListening();
    }

    @Override
    protected void onPause() {
        stopListening();
        super.onPause();
    }

    private int selectedExt() {
        int id = extGroup.getCheckedRadioButtonId();
        if (id == R.id.rb_ext2) return 2;
        if (id == R.id.rb_ext3) return 3;
        return 1;
    }

    private void updatePreview(String text) {
        preview.setText(KeypadEncoder.encode(text));
        String bad = KeypadEncoder.findUnsupported(text);
        warn.setText(bad.length() == 0 ? "" : getString(R.string.warn_unsupported, bad));
    }

    // ---- מצב שיחה ----
    @SuppressWarnings("deprecation")
    private void startListening() {
        if (listener != null || !PermissionUtil.hasAll(this, PermissionUtil.ALL)) return;
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null) return;
        listener = new PhoneStateListener() {
            @Override public void onCallStateChanged(int state, String incomingNumber) {
                // ה-receiver הסטטי מעדכן את CallTracker; נותנים לו רגע לפני הרענון
                handler.removeCallbacks(refreshRunnable);
                handler.postDelayed(refreshRunnable, 600);
            }
        };
        tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    @SuppressWarnings("deprecation")
    private void stopListening() {
        handler.removeCallbacks(refreshRunnable);
        if (listener == null) return;
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) tm.listen(listener, PhoneStateListener.LISTEN_NONE);
        listener = null;
    }

    private void refreshStatus() {
        if (!PermissionUtil.hasAll(this, PermissionUtil.ALL)) {
            status.setText(R.string.status_need_permission);
            return;
        }
        String number = Prefs.getNumber(this);
        if (number.length() == 0) {
            status.setText(R.string.status_no_number);
            return;
        }
        switch (CallTracker.status(this)) {
            case IN_CALL_TARGET: status.setText(R.string.status_in_target); break;
            case IN_CALL_OTHER: status.setText(R.string.status_in_other); break;
            case IN_CALL_UNKNOWN: status.setText(R.string.status_in_unknown); break;
            default: status.setText(getString(R.string.status_idle, number, selectedExt())); break;
        }
    }

    // ---- שליחה ----
    private void trySend() {
        String body = KeypadEncoder.encode(input.getText().toString());
        if (body.length() <= 1) { // רק '#': אין מה לשלוח
            Toast.makeText(this, R.string.toast_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        // מבקשים את כל ההרשאות מראש; אם נדחו - לא שולחים כלל (אין נתיב גיבוי ידני)
        if (!PermissionUtil.hasAll(this, PermissionUtil.ALL)) {
            pendingSend = true;
            PermissionUtil.requestAll(this, PermissionUtil.ALL);
            return;
        }
        doSend(body);
    }

    private void doSend(String body) {
        String number = Prefs.getNumber(this);
        if (number.length() == 0) {
            Toast.makeText(this, R.string.toast_no_number, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        switch (CallTracker.status(this)) {
            case IDLE: {
                String dial = Dialer.buildDialString(number, Prefs.getP1(this), Prefs.getP2(this),
                        Prefs.getP3(this), selectedExt(), body);
                if (!Dialer.dial(this, dial)) {
                    Toast.makeText(this, R.string.toast_airplane, Toast.LENGTH_LONG).show();
                }
                break;
            }
            case IN_CALL_TARGET:
                // כבר בשיחה עם הקו: לא מחייגים ולא בוחרים שלוחה - רק הרצף
                InCallSendService.start(this, body);
                break;
            case IN_CALL_OTHER:
                Toast.makeText(this, R.string.toast_other_call, Toast.LENGTH_LONG).show();
                break;
            default:
                Toast.makeText(this, R.string.toast_unknown_call, Toast.LENGTH_LONG).show();
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PermissionUtil.REQUEST_CODE) return;
        boolean granted = PermissionUtil.hasAll(this, PermissionUtil.ALL);
        if (granted) {
            startListening();
            refreshStatus();
            if (pendingSend) doSend(KeypadEncoder.encode(input.getText().toString()));
        } else if (pendingSend) {
            Toast.makeText(this, R.string.toast_no_permission, Toast.LENGTH_LONG).show();
        }
        pendingSend = false;
    }
}
