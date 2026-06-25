package com.sdv.lichnoti

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "lich_noti_prefs"
        private const val KEY_CREW = "selected_crew"
        private const val KEY_DAY_HOUR = "day_notification_hour"
        private const val KEY_DAY_MINUTE = "day_notification_minute"
        private const val KEY_NIGHT_HOUR = "night_notification_hour"
        private const val KEY_NIGHT_MINUTE = "night_notification_minute"
        private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        private const val KEY_TARGET_PACKAGE = "target_package"
        private const val KEY_OPEN_SELF = "open_self"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_CALENDAR_MODE = "calendar_mode"
        private const val KEY_CALENDAR_VISIBLE = "calendar_visible"
        private const val KEY_DAY_COLOR = "day_color"
        private const val KEY_NIGHT_COLOR = "night_color"
        private const val KEY_HO_BORDER_COLOR = "ho_border_color"
        private const val KEY_NOTIFICATION_CONTENT = "notification_content"
        private const val KEY_SNOOZE_DURATION = "snooze_duration"
        private const val KEY_LAST_UPDATE_CHECK_TIME = "last_update_check_time"
        private const val KEY_HIDE_HOLIDAY_SHIFT = "hide_holiday_shift"
        private const val KEY_AUTO_LOCK_SAMSUNG = "auto_lock_samsung"
        private const val KEY_MERGE_MONTHS = "merge_months"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var selectedCrew: String
        get() = prefs.getString(KEY_CREW, "A") ?: "A"
        set(value) = prefs.edit().putString(KEY_CREW, value).apply()

    var dayNotificationHour: Int
        get() = prefs.getInt(KEY_DAY_HOUR, 6)
        set(value) = prefs.edit().putInt(KEY_DAY_HOUR, value).apply()

    var dayNotificationMinute: Int
        get() = prefs.getInt(KEY_DAY_MINUTE, 30)
        set(value) = prefs.edit().putInt(KEY_DAY_MINUTE, value).apply()

    var nightNotificationHour: Int
        get() = prefs.getInt(KEY_NIGHT_HOUR, 18)
        set(value) = prefs.edit().putInt(KEY_NIGHT_HOUR, value).apply()

    var nightNotificationMinute: Int
        get() = prefs.getInt(KEY_NIGHT_MINUTE, 30)
        set(value) = prefs.edit().putInt(KEY_NIGHT_MINUTE, value).apply()

    var notificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_ENABLED, value).apply()

    var targetPackage: String
        get() {
            val value = prefs.getString(KEY_TARGET_PACKAGE, "com.samsung.s1.vselflock") ?: "com.samsung.s1.vselflock"
            return if (value.isBlank()) "com.samsung.s1.vselflock" else value
        }
        set(value) = prefs.edit().putString(KEY_TARGET_PACKAGE, value).apply()

    var openSelf: Boolean
        get() = prefs.getBoolean(KEY_OPEN_SELF, true)
        set(value) = prefs.edit().putBoolean(KEY_OPEN_SELF, value).apply()

    var openOtherApp: Boolean
        get() = !openSelf
        set(value) {
            openSelf = !value
        }

    var darkMode: String
        get() = prefs.getString(KEY_DARK_MODE, "light") ?: "light"
        set(value) = prefs.edit().putString(KEY_DARK_MODE, value).apply()

    var calendarMode: String
        get() = prefs.getString(KEY_CALENDAR_MODE, "month") ?: "month"
        set(value) = prefs.edit().putString(KEY_CALENDAR_MODE, value).apply()

    var calendarVisible: Boolean
        get() = prefs.getBoolean(KEY_CALENDAR_VISIBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_CALENDAR_VISIBLE, value).apply()

    var dayColor: String
        get() {
            val color = prefs.getString(KEY_DAY_COLOR, "#15803D") ?: "#15803D"
            if (color == "#F97316") {
                prefs.edit().putString(KEY_DAY_COLOR, "#15803D").apply()
                return "#15803D"
            }
            return color
        }
        set(value) = prefs.edit().putString(KEY_DAY_COLOR, value).apply()

    var nightColor: String
        get() {
            val color = prefs.getString(KEY_NIGHT_COLOR, "#6D28D9") ?: "#6D28D9"
            if (color == "#3B82F6") {
                prefs.edit().putString(KEY_NIGHT_COLOR, "#6D28D9").apply()
                return "#6D28D9"
            }
            return color
        }
        set(value) = prefs.edit().putString(KEY_NIGHT_COLOR, value).apply()

    var hoBorderColor: String
        get() = prefs.getString(KEY_HO_BORDER_COLOR, "#EC4899") ?: "#EC4899"
        set(value) = prefs.edit().putString(KEY_HO_BORDER_COLOR, value).apply()

    var notificationContent: String
        get() = prefs.getString(KEY_NOTIFICATION_CONTENT, "Hãy dán cam hoặc mở app MDM") ?: "Hãy dán cam hoặc mở app MDM"
        set(value) = prefs.edit().putString(KEY_NOTIFICATION_CONTENT, value).apply()

    var snoozeDuration: Int
        get() = prefs.getInt(KEY_SNOOZE_DURATION, 10)
        set(value) = prefs.edit().putInt(KEY_SNOOZE_DURATION, value).apply()

    var lastUpdateCheckTime: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_TIME, value).apply()

    var hideHolidayShift: Boolean
        get() = prefs.getBoolean(KEY_HIDE_HOLIDAY_SHIFT, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_HOLIDAY_SHIFT, value).apply()

    var autoLockSamsung: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOCK_SAMSUNG, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LOCK_SAMSUNG, value).apply()

    var mergeMonths: Boolean
        get() = prefs.getBoolean(KEY_MERGE_MONTHS, false)
        set(value) = prefs.edit().putBoolean(KEY_MERGE_MONTHS, value).apply()
}
