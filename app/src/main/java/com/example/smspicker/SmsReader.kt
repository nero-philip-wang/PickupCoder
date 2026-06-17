package com.example.smspicker

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.Telephony

object SmsReader {

    fun readInbox(context: Context, days: Int = 7, limit: Int = 500): List<SmsInfo> {
        val result = mutableListOf<SmsInfo>()
        val contentResolver: ContentResolver = context.contentResolver
        val uri: Uri = Telephony.Sms.CONTENT_URI

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        val cutoffTime = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        val selection = "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(
            Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
            cutoffTime.toString()
        )
        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"

        contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

            while (cursor.moveToNext()) {
                val sender = if (addressIdx >= 0) cursor.getString(addressIdx) ?: "" else ""
                val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) ?: "" else ""
                val time = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()

                if (SmsParser.isExpressSms(body, sender)) {
                    val info = SmsParser.parse(body, sender, time)
                    if (info != null) {
                        result.add(info)
                    }
                }
            }
        }
        return result
    }
}
