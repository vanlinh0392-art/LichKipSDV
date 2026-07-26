package com.sdv.lichnoti

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.UserManager

/**
 * Stores alarm-critical state in device-protected storage so it is available between reboot
 * and the first user unlock. Existing credential-protected preferences are migrated once while
 * the user is unlocked.
 */
object DirectBootStorage {
    private const val META_PREFS = "direct_boot_storage_meta"
    private const val MIGRATED_PREFIX = "migrated_"

    fun preferences(context: Context, name: String): SharedPreferences {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        }

        val deviceContext = appContext.createDeviceProtectedStorageContext()
        if (isUserUnlocked(appContext)) {
            val meta = deviceContext.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
            val migrationKey = MIGRATED_PREFIX + name
            if (!meta.getBoolean(migrationKey, false)) {
                runCatching { deviceContext.moveSharedPreferencesFrom(appContext, name) }
                meta.edit().putBoolean(migrationKey, true).commit()
            }
        }
        return deviceContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    fun isUserUnlocked(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
        return context.getSystemService(UserManager::class.java)?.isUserUnlocked == true
    }
}
