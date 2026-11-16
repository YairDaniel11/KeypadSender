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
import android.view.KeyEvent;
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
        View settings = findViewById(R.id.btn_settings);
        View history = findViewById(R.id.btn_history);
        Button clear = (Button) findViewById(R.id.btn_clear);

        buildExtButtons();

        // שחזור הטקסט האחרון (גם אחרי שהמערכת סגרה את האפליקציה ברקע)
        String draft = Prefs.getDraft(this);
        if (draft.length() > 0) {
            input.setText(draft);
            input.setSelection(draft.length());
        }

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                updatePreview(s.toString());
                Prefs.setDraft(MainActivity.this, s.toString());
            }
        });
        updatePreview(input.getText().toString());

        send.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { trySend(); }
        });
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openSettings(); }
        });
        history.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivityForResult(new Intent(MainActivity.this, HistoryActivity.class), REQ_HISTORY);
            }
        });
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                input.setText("");
                input.requestFocus();
            }
        });

        gotoCall = findViewById(R.id.btn_goto_call);
        gotoCall.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { gotoActiveCall(); }
        });

        // פוקוס התחלתי: שדה הטקסט (הפעולה הנפוצה); השלוחה זמינה בחץ למעלה
        input.requestFocus();
    }

    private View gotoCall;

    /** מעלה את מסך השיחה הפעילה לחזית (TelecomManager, ואם אין/לא עבד - רוט עם וידוא). */
    private void gotoActiveCall() {
        if (CallTracker.status(this) == CallTracker.Status.IDLE) {
            Toast.makeText(this, R.string.toast_no_active_call, Toast.LENGTH_SHORT).show();
            return;
        }
        final Context app = getApplicationContext();
        final boolean root = Prefs.useRoot(this);
        new Thread(new Runnable() {
            @Override public void run() {
                if (!root) {
                    if (!CallScreen.moveCallTaskToFront(app)) CallScreen.showViaTelecom(app);
                    return;
                }
                RootShell sh = RootShell.open();
                if (sh == null) { CallScreen.showViaTelecom(app); return; }
                try {
                    if (!CallScreen.bringToFront(app, sh)) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                Toast.makeText(MainActivity.this, R.string.toast_call_screen_not_found,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                } catch (Exception ignored) {
                } finally {
                    sh.close();
                }
            }
        }, "goto-call").start();
    }

    /** חזור במסך הראשי = יציאה מהאפליקציה (למסך הבית), לא חזרה למסך קודם. */
    @Override
    public void onBackPressed() {
        finish();
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
    }

    private static final int REQ_HISTORY = 77;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_HISTORY && resultCode == RESULT_OK && data != null) {
            String t = data.getStringExtra(HistoryActivity.EXTRA_TEXT);
            if (t != null) {
                input.setText(t);
                input.setSelection(t.length());
                input.requestFocus();
            }
        }
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    /** חזור בחלונית טקסט = מחיקה (רק בשדה ריק יוצאים); מקש תפריט = הגדרות. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (KeyUtil.backAsDelete(this, ev)) return true;
        if (ev.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) openSettings();
            return true;
        }
        return super.dispatchKeyEvent(ev);
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildExtButtons(); // ההגדרות ייתכן ששונו
        refreshStatus();
        startListening();
    }

    @Override
    protected void onPause() {
        stopListening();
        super.onPause();
    }

    /** בונה את כפתורי הבחירה לפי ההגדרות (כמות, כותרות). עד 4 - בשורה; יותר - בעמודה. */
    private void buildExtButtons() {
        extGroup.setOnCheckedChangeListener(null);
        extGroup.removeAllViews();
        int count = Prefs.getExtCount(this);
        boolean horizontal = count <= 4;
        extGroup.setOrientation(horizontal ? RadioGroup.HORIZONTAL : RadioGroup.VERTICAL);
        int sel = Prefs.getExt(this);
        final int[] ids = new int[count];
        for (int i = 1; i <= count; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(i == 1 ? R.id.rb_ext1 : View.generateViewId());
            ids[i - 1] = rb.getId();
            rb.setText(Prefs.getExtTitle(this, i));
            rb.setTextSize(12);
            rb.setMinHeight((int) (40 * getResources().getDisplayMetrics().density));
            rb.setTextColor(ThemeUtil.resolveColor(this, R.attr.colorTextPrimary));
            RadioGroup.LayoutParams lp = horizontal
                    ? new RadioGroup.LayoutParams(0, RadioGroup.LayoutParams.WRAP_CONTENT, 1f)
                    : new RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT,
                            RadioGroup.LayoutParams.WRAP_CONTENT);
            rb.setLayoutParams(lp);
            if (horizontal || i == 1) rb.setNextFocusUpId(R.id.btn_goto_call);
            if (horizontal || i == count) rb.setNextFocusDownId(R.id.input);
            extGroup.addView(rb);
            if (i == sel) rb.setChecked(true);
        }
        input.setNextFocusUpId(R.id.rb_ext1);
        updatePreview(input.getText().toString());
        extGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                Prefs.setExt(MainActivity.this, selectedExt());
                updatePreview(input.getText().toString());
                refreshStatus();
            }
        });
    }

    private int selectedExt() {
        int id = extGroup.getCheckedRadioButtonId();
        for (int i = 0; i < extGroup.getChildCount(); i++) {
            if (extGroup.getChildAt(i).getId() == id) return i + 1;
        }
        return 1;
    }

    private void updatePreview(String text) {
        String map = Prefs.getLetterMap(this);
        preview.setText(KeypadEncoder.encodeBody(text, map) + Prefs.getExtSuffix(this, selectedExt()));
        String bad = KeypadEncoder.findUnsupported(text, map);
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
        String number = Prefs.effectiveNumber(this, selectedExt());
        if (number.length() == 0) {
            status.setText(R.string.status_no_number);
            return;
        }
        switch (CallTracker.status(this)) {
            case IN_CALL_TARGET: status.setText(R.string.status_in_target); break;
            case IN_CALL_OTHER: status.setText(R.string.status_in_other); break;
            case IN_CALL_UNKNOWN: status.setText(R.string.status_in_unknown); break;
            default: status.setText(getString(R.string.status_idle, number,
                    Prefs.getExtTitle(this, selectedExt()))); break;
        }
    }

    // ---- שליחה ----
    /** הטקסט המקודד (לפי מפת האותיות) בלי סיומת. */
    private String encodedText() {
        return KeypadEncoder.encodeBody(input.getText().toString(), Prefs.getLetterMap(this));
    }

    /** מה שנשלח בפועל אחרי הרצף: הטקסט המקודד + הסיומת של הכפתור הנבחר. */
    private String fullBody() {
        return encodedText() + Prefs.getExtSuffix(this, selectedExt());
    }

    private void trySend() {
        if (encodedText().length() == 0) { // אין מה לשלוח
            Toast.makeText(this, R.string.toast_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        Prefs.addHistory(this, input.getText().toString());
        // מבקשים את כל ההרשאות מראש; אם נדחו - לא שולחים כלל (אין נתיב גיבוי ידני)
        if (!PermissionUtil.hasAll(this, PermissionUtil.ALL)) {
            pendingSend = true;
            PermissionUtil.requestAll(this, PermissionUtil.ALL);
            return;
        }
        doSend();
    }

    private void doSend() {
        int ext = selectedExt();
        String number = Prefs.effectiveNumber(this, ext);
        String body = fullBody();
        String seq = Prefs.getExtSeq(this, ext);
        if (number.length() == 0) {
            Toast.makeText(this, R.string.toast_no_number, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        switch (CallTracker.status(this)) {
            case IDLE: {
                boolean root = Prefs.useRoot(this);
                // עם רוט: מחייגים את המספר בלבד (מחרוזת עם פסיקים נדחית בחלק מהמכשירים
                // כ"מספר לא תקין") ואחר כך שולחים את הרצף וההקשות כשהשיחה מתחברת.
                String dial = root
                        ? number.replaceAll("[\\s-]", "")
                        : Dialer.buildDialString(number, seq, body);
                if (!Dialer.dial(this, dial)) {
                    Toast.makeText(this, R.string.toast_airplane, Toast.LENGTH_LONG).show();
                } else if (root) {
                    SeqUtil.Script sc = SeqUtil.toScript(seq, body);
                    InCallSendService.startScripted(this, sc.parts, sc.delays);
                }
                break;
            }
            case IN_CALL_TARGET:
                if (!CallTracker.activeMatches(this, number)) { // שיחה עם קו אחר מהכפתור הנבחר
                    Toast.makeText(this, R.string.toast_other_call, Toast.LENGTH_LONG).show();
                    break;
                }
                // כבר בשיחה עם הקו: לא מחייגים ולא שולחים רצף - רק הטקסט
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
            if (pendingSend) doSend();
        } else if (pendingSend) {
            Toast.makeText(this, R.string.toast_no_permission, Toast.LENGTH_LONG).show();
        }
        pendingSend = false;
    }
}
