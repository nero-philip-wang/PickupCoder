package com.example.smspicker

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

object SmsStorage {

    private const val PREF_NAME = "sms_picker_storage"
    private const val KEY_OUT_IDS = "out_ids"
    private const val KEY_READ_DAYS = "read_days"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isOut(id: String): Boolean {
        return prefs.getStringSet(KEY_OUT_IDS, emptySet())?.contains(id) == true
    }

    fun markOut(id: String) {
        val set = prefs.getStringSet(KEY_OUT_IDS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.add(id)
        prefs.edit().putStringSet(KEY_OUT_IDS, set).apply()
    }

    fun markOut(ids: List<String>) {
        val set = prefs.getStringSet(KEY_OUT_IDS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.addAll(ids)
        prefs.edit().putStringSet(KEY_OUT_IDS, set).apply()
    }

    fun undoOut(id: String) {
        val set = prefs.getStringSet(KEY_OUT_IDS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.remove(id)
        prefs.edit().putStringSet(KEY_OUT_IDS, set).apply()
    }

    fun undoOut(ids: List<String>) {
        val set = prefs.getStringSet(KEY_OUT_IDS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        ids.forEach { set.remove(it) }
        prefs.edit().putStringSet(KEY_OUT_IDS, set).apply()
    }

    fun getReadDays(): Int {
        return prefs.getInt(KEY_READ_DAYS, 7)
    }

    fun setReadDays(days: Int) {
        prefs.edit().putInt(KEY_READ_DAYS, days).apply()
    }

    fun generateId(body: String, time: Long): String {
        val raw = "$body|$time"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
