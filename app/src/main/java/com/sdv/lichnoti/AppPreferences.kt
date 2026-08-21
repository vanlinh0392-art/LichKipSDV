package com.sdv.lichnoti

import android.content.Context
import android.content.SharedPreferences

data class OffDayAlarmItem(
    val time: String,
    val enabled: Boolean = true,
    val skipSaturday: Boolean = false,
    val skipSunday: Boolean = false
)

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
        private const val KEY_HO_BORDER_WIDTH = "ho_border_width"
        private const val KEY_NOTIFICATION_CONTENT = "notification_content"
        private const val KEY_SNOOZE_DURATION = "snooze_duration"
        private const val KEY_LAST_UPDATE_CHECK_TIME = "last_update_check_time"
        private const val KEY_HIDE_HOLIDAY_SHIFT = "hide_holiday_shift"
        private const val KEY_AUTO_LOCK_SAMSUNG = "auto_lock_samsung"
        private const val KEY_MERGE_MONTHS = "merge_months"
        private const val KEY_HOLIDAY_ALERT_ENABLED = "holiday_alert_enabled"
        private const val KEY_LUNAR_REMINDER_MODE = "lunar_reminder_mode"
        private const val KEY_FORCE_MAX_VOLUME = "force_max_volume"
        private const val KEY_AUTO_SEND_MDM_ON_SCREEN = "auto_send_mdm_on_screen"
        private const val KEY_OFF_DAY_ALARM_ENABLED = "off_day_alarm_enabled"
        private const val KEY_OFF_DAY_ALARM_TIMES = "off_day_alarm_times"
    }

    private val prefs: SharedPreferences = DirectBootStorage.preferences(context, PREFS_NAME)

    init {
        // Tự động di chuyển dữ liệu off-day alarm nếu vô tình được lưu ở file prefs tạm
        try {
            val tempPrefs = DirectBootStorage.preferences(context, "sdv_lich_noti_prefs")
            if (tempPrefs.contains(KEY_OFF_DAY_ALARM_ENABLED) || tempPrefs.contains(KEY_OFF_DAY_ALARM_TIMES)) {
                val editor = prefs.edit()
                if (!prefs.contains(KEY_OFF_DAY_ALARM_ENABLED) && tempPrefs.contains(KEY_OFF_DAY_ALARM_ENABLED)) {
                    editor.putBoolean(KEY_OFF_DAY_ALARM_ENABLED, tempPrefs.getBoolean(KEY_OFF_DAY_ALARM_ENABLED, false))
                }
                if (!prefs.contains(KEY_OFF_DAY_ALARM_TIMES) && tempPrefs.contains(KEY_OFF_DAY_ALARM_TIMES)) {
                    editor.putString(KEY_OFF_DAY_ALARM_TIMES, tempPrefs.getString(KEY_OFF_DAY_ALARM_TIMES, "07:30:1"))
                }
                editor.apply()
            }
        } catch (_: Exception) {}
    }

    var selectedCrew: String
        get() = prefs.getString(KEY_CREW, "A") ?: "A"
        set(value) = prefs.edit().putString(KEY_CREW, value).apply()

    var dayNotificationHour: Int
        get() = prefs.getInt(KEY_DAY_HOUR, 7)
        set(value) = prefs.edit().putInt(KEY_DAY_HOUR, value).apply()

    var dayNotificationMinute: Int
        get() = prefs.getInt(KEY_DAY_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_DAY_MINUTE, value).apply()

    var nightNotificationHour: Int
        get() = prefs.getInt(KEY_NIGHT_HOUR, 19)
        set(value) = prefs.edit().putInt(KEY_NIGHT_HOUR, value).apply()

    var nightNotificationMinute: Int
        get() = prefs.getInt(KEY_NIGHT_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_NIGHT_MINUTE, value).apply()

    var notificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_ENABLED, value).apply()

    var targetPackage: String
        get() = prefs.getString(KEY_TARGET_PACKAGE, "com.samsung.s1.vselflock") ?: "com.samsung.s1.vselflock"
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

    var hoBorderWidth: Int
        get() = prefs.getInt(KEY_HO_BORDER_WIDTH, 2)
        set(value) = prefs.edit().putInt(KEY_HO_BORDER_WIDTH, value).apply()

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

    var holidayAlertEnabled: Boolean
        get() = prefs.getBoolean(KEY_HOLIDAY_ALERT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HOLIDAY_ALERT_ENABLED, value).apply()

    var lunarReminderMode: Int
        get() = prefs.getInt(KEY_LUNAR_REMINDER_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_LUNAR_REMINDER_MODE, value).apply()

    var forceMaxVolume: Boolean
        get() = prefs.getBoolean(KEY_FORCE_MAX_VOLUME, false)
        set(value) = prefs.edit().putBoolean(KEY_FORCE_MAX_VOLUME, value).apply()

    var autoSendMdmOnScreen: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SEND_MDM_ON_SCREEN, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SEND_MDM_ON_SCREEN, value).apply()

    var offDayAlarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_OFF_DAY_ALARM_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_OFF_DAY_ALARM_ENABLED, value).apply()

    var offDayAlarms: List<OffDayAlarmItem>
        get() {
            val raw = prefs.getString(KEY_OFF_DAY_ALARM_TIMES, "07:30:1:0:0") ?: "07:30:1:0:0"
            if (raw.isBlank()) return emptyList()
            return raw.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { entry ->
                    val parts = entry.split(":")
                    when (parts.size) {
                        2 -> {
                            val time = "${parts[0]}:${parts[1]}"
                            if (time.matches(Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$"))) {
                                OffDayAlarmItem(time, enabled = true, skipSaturday = false, skipSunday = false)
                            } else null
                        }
                        3 -> {
                            val time = "${parts[0]}:${parts[1]}"
                            val isEnabled = parts[2] != "0"
                            if (time.matches(Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$"))) {
                                OffDayAlarmItem(time, enabled = isEnabled, skipSaturday = false, skipSunday = false)
                            } else null
                        }
                        4 -> {
                            val time = "${parts[0]}:${parts[1]}"
                            val isEnabled = parts[2] != "0"
                            val skipWeekend = parts[3] == "1"
                            if (time.matches(Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$"))) {
                                OffDayAlarmItem(time, enabled = isEnabled, skipSaturday = skipWeekend, skipSunday = skipWeekend)
                            } else null
                        }
                        5 -> {
                            val time = "${parts[0]}:${parts[1]}"
                            val isEnabled = parts[2] != "0"
                            val skipSat = parts[3] == "1"
                            val skipSun = parts[4] == "1"
                            if (time.matches(Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$"))) {
                                OffDayAlarmItem(time, enabled = isEnabled, skipSaturday = skipSat, skipSunday = skipSun)
                            } else null
                        }
                        else -> null
                    }
                }
                .distinctBy { it.time }
                .sortedBy { it.time }
        }
        set(value) {
            val formatted = value.distinctBy { it.time }
                .sortedBy { it.time }
                .joinToString(",") { 
                    "${it.time}:${if (it.enabled) "1" else "0"}:${if (it.skipSaturday) "1" else "0"}:${if (it.skipSunday) "1" else "0"}" 
                }
            prefs.edit().putString(KEY_OFF_DAY_ALARM_TIMES, formatted).apply()
        }

    var offDayAlarmTimes: List<String>
        get() = offDayAlarms.map { it.time }
        set(value) {
            offDayAlarms = value.map { OffDayAlarmItem(it, true) }
        }

    fun getActiveOffDayAlarmTimes(): List<String> {
        return offDayAlarms.filter { it.enabled }.map { it.time }
    }

    fun getActiveOffDayAlarmTimesForDay(dayOfWeekValue: Int): List<String> {
        return offDayAlarms.filter { item ->
            if (!item.enabled) return@filter false
            if (dayOfWeekValue == 6 && item.skipSaturday) return@filter false
            if (dayOfWeekValue == 7 && item.skipSunday) return@filter false
            true
        }.map { it.time }
    }

    fun addOffDayAlarm(time: String, enabled: Boolean = true, skipSat: Boolean = false, skipSun: Boolean = false) {
        val current = offDayAlarms.toMutableList()
        current.removeAll { it.time == time }
        current.add(OffDayAlarmItem(time, enabled, skipSat, skipSun))
        offDayAlarms = current
    }

    fun toggleOffDayAlarm(time: String, enabled: Boolean) {
        val current = offDayAlarms.map {
            if (it.time == time) it.copy(enabled = enabled) else it
        }
        offDayAlarms = current
    }

    fun toggleOffDayAlarmSkipSaturday(time: String, skipSaturday: Boolean) {
        val current = offDayAlarms.map {
            if (it.time == time) it.copy(skipSaturday = skipSaturday) else it
        }
        offDayAlarms = current
    }

    fun toggleOffDayAlarmSkipSunday(time: String, skipSunday: Boolean) {
        val current = offDayAlarms.map {
            if (it.time == time) it.copy(skipSunday = skipSunday) else it
        }
        offDayAlarms = current
    }

    fun updateOffDayAlarmTime(oldTime: String, newTime: String) {
        val current = offDayAlarms.map {
            if (it.time == oldTime) it.copy(time = newTime) else it
        }.distinctBy { it.time }.sortedBy { it.time }
        offDayAlarms = current
    }

    fun removeOffDayAlarm(time: String) {
        val current = offDayAlarms.filter { it.time != time }
        offDayAlarms = current
    }

    fun addOffDayAlarmTime(time: String) {
        addOffDayAlarm(time, true)
    }

    fun removeOffDayAlarmTime(time: String) {
        removeOffDayAlarm(time)
    }
}
