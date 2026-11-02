package com.acepanel.client.platform

import java.util.prefs.Preferences

actual class PlatformSettings actual constructor() {
    private val prefs: Preferences = Preferences.userRoot().node("com/acepanel/client")

    actual fun getString(key: String): String? = prefs.get(key, null)
    actual fun putString(key: String, value: String) = prefs.put(key, value)
    actual fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    actual fun putBoolean(key: String, value: Boolean) = prefs.putBoolean(key, value)
    actual fun remove(key: String) = prefs.remove(key)
}

actual fun appDataDir(): String =
    System.getProperty("acepanel.data.dir") ?: (System.getProperty("user.home") + "/.acepanel")
