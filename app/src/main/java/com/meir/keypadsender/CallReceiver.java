package com.meir.keypadsender;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

/** receiver סטטי - מקבל את שידורי השיחה גם כשהאפליקציה לא פתוחה. לא משנה ולא חוסם שיחות. */
public class CallReceiver extends BroadcastReceiver {

    // literal ולא TelephonyManager.ACTION_PHONE_STATE_CHANGED, שנוסף רק ב-API 21
    private static final String ACTION_PHONE_STATE = "android.intent.action.PHONE_STATE";

    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (Intent.ACTION_NEW_OUTGOING_CALL.equals(action)) {
            String number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            if (number == null) number = getResultData();
            CallTracker.onOutgoing(context, number);
        } else if (ACTION_PHONE_STATE.equals(action)) {
            CallTracker.onPhoneState(context,
                    intent.getStringExtra(TelephonyManager.EXTRA_STATE),
                    intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER));
        }
    }
}
