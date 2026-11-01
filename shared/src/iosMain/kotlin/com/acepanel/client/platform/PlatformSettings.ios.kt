package com.acepanel.client.platform

import platform.Foundation.NSUserDefaults

actual class PlatformSettings actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String): String? = defaults.stringForKey(key)
    actual fun putString(key: String, value: String) = defaults.setObject(value, forKey = key)
    actual fun getBoolean(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

    actual fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, forKey = key)
    actual fun remove(key: String) = defaults.removeObjectForKey(key)
}

actual fun appDataDir(): String =
    (platform.Foundation.NSSearchPathForDirectoriesInDomains(
        platform.Foundation.NSDocumentDirectory,
        platform.Foundation.NSUserDomainMask,
        true,
    ).firstOrNull() as? String) ?: ""
