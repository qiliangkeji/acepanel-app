package com.acepanel.client.platform

import android.content.Context
import android.content.SharedPreferences

object AndroidAppContext {
    lateinit var context: Context
}

actual class PlatformSettings actual constructor() {
    private val prefs: SharedPreferences
        get() = AndroidAppContext.context.getSharedPreferences("acepanel_client", Context.MODE_PRIVATE)

    actual fun getString(key: String): String? = prefs.getString(key, null)
    actual fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    actual fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    actual fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

actual fun appDataDir(): String = AndroidAppContext.context.filesDir.absolutePath
