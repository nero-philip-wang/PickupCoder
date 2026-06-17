package com.example.smspicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val fullBody = StringBuilder()
        var sender = ""
        var time: Long = System.currentTimeMillis()

        for (msg: SmsMessage in messages) {
            fullBody.append(msg.messageBody)
            sender = msg.originatingAddress ?: ""
            time = msg.timestampMillis
        }

        val body = fullBody.toString()
        if (SmsParser.isExpressSms(body, sender)) {
            val info = SmsParser.parse(body, sender, time)
            info?.let {
                val broadcastIntent = Intent(ACTION_NEW_EXPRESS_SMS).apply {
                    setPackage(context.packageName)
                    putExtra(EXTRA_SMS_INFO, it)
                }
                context.sendBroadcast(broadcastIntent)
            }
        }
    }

    companion object {
        const val ACTION_NEW_EXPRESS_SMS = "com.example.smspicker.NEW_EXPRESS_SMS"
        const val EXTRA_SMS_INFO = "extra_sms_info"
    }
}
