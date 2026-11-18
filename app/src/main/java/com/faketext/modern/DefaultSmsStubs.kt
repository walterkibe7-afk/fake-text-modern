package com.faketext.modern

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, sms.originatingAddress ?: "")
                    put(Telephony.Sms.BODY, sms.messageBody ?: "")
                    put(Telephony.Sms.DATE, sms.timestampMillis)
                    put(Telephony.Sms.READ, 0)
                }
                runCatching {
                    context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                }
            }
        }
    }
}

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}

class ComposeSmsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
