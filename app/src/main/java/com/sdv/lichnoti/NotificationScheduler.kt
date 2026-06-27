package com.sdv.lichnoti

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object NotificationScheduler {

    private const val TAG = "NotificationScheduler"
    private const val ALARM_REQUEST_CODE = 2001

    fun scheduleNext(context: Context) {
        val prefs = AppPreferences(context)

        if (!prefs.notificationEnabled) {
            cancelAlarm(context)
            return
        }

        val crewId = prefs.selectedCrew
        val now = LocalDateTime.now()
        val today = now.toLocalDate()

        // Find the next alarm time
        val nextAlarmTime = findNextAlarmTime(crewId, prefs, today, now)

        if (nextAlarmTime != null) {
            val millis = nextAlarmTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            setExactAlarm(context, millis)
            Log.d(TAG, "Alarm scheduled for: $nextAlarmTime")
        } else {
            Log.d(TAG, "No alarm to schedule")
        }
    }

    private fun findNextAlarmTime(
        crewId: String,
        prefs: AppPreferences,
        today: LocalDate,
        now: LocalDateTime
    ): LocalDateTime? {
        // Check today and the next 14 days
        for (dayOffset in 0..14) {
            val date = today.plusDays(dayOffset.toLong())
            val shift = ShiftCalculator.getActualShift(crewId, date)

            when (shift) {
                ShiftCalculator.ShiftType.NGAY -> {
                    val alarmTime = date.atTime(prefs.dayNotificationHour, prefs.dayNotificationMinute)
                    if (alarmTime.isAfter(now)) {
                        return alarmTime
                    }
                }
                ShiftCalculator.ShiftType.DEM -> {
                    val alarmTime = date.atTime(prefs.nightNotificationHour, prefs.nightNotificationMinute)
                    if (alarmTime.isAfter(now)) {
                        return alarmTime
                    }
                }
                ShiftCalculator.ShiftType.NGHI -> {
                    // Skip rest days
                }
            }
        }
        return null
    }

    private fun setExactAlarm(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                } else {
                    // Fallback to inexact alarm
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot schedule exact alarm", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
            )
        }
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun scheduleSnooze(context: Context, minutes: Int) {
        val triggerAtMillis = System.currentTimeMillis() + minutes * 60 * 1000
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
            Log.d(TAG, "Đã lên lịch nhắc lại báo thức sau $minutes phút")
        } catch (e: SecurityException) {
            Log.e(TAG, "Không thể lên lịch exact alarm cho snooze", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
            )
        }
    }

    private const val LUNAR_ALARM_REQUEST_CODE = 2002

    fun scheduleLunarAlarm(context: Context) {
        val prefs = AppPreferences(context)
        if (prefs.lunarReminderMode == 0) {
            cancelLunarAlarm(context)
            return
        }

        val now = LocalDateTime.now()
        val nextLunarAlarm = findNextLunarAlarmTime(prefs, now)

        if (nextLunarAlarm != null) {
            val millis = nextLunarAlarm.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            setExactLunarAlarm(context, millis)
            Log.d(TAG, "Lunar alarm scheduled for: $nextLunarAlarm")
        } else {
            Log.d(TAG, "No lunar alarm to schedule")
        }
    }

    private fun findNextLunarAlarmTime(prefs: AppPreferences, now: LocalDateTime): LocalDateTime? {
        val mode = prefs.lunarReminderMode // 0: off, 1: cung ngay, 2: truoc 1 ngay
        if (mode == 0) return null

        val today = now.toLocalDate()
        // Duyệt tối đa 35 ngày tiếp theo để tìm ngày thoả mãn
        for (i in 0..35) {
            val date = today.plusDays(i.toLong())
            val alarmTime = date.atTime(6, 30)
            if (alarmTime.isBefore(now)) continue // Đã qua thời gian báo hôm nay (6h30)

            val lunar = VietCalendar.convertSolar2Lunar(date.dayOfMonth, date.monthValue, date.year, 7.0)
            val lunarDay = lunar[0]

            if (mode == 1) { // Báo cùng ngày
                if (lunarDay == 1 || lunarDay == 15) {
                    return alarmTime
                }
            } else if (mode == 2) { // Trước 1 ngày
                if (lunarDay == 14) {
                    return alarmTime
                }
                // Kiểm tra xem ngày mai có phải mùng 1 âm không
                val tomorrow = date.plusDays(1)
                val tomLunar = VietCalendar.convertSolar2Lunar(tomorrow.dayOfMonth, tomorrow.monthValue, tomorrow.year, 7.0)
                if (tomLunar[0] == 1) {
                    return alarmTime
                }
            }
        }
        return null
    }

    private fun setExactLunarAlarm(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_LUNAR_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, LUNAR_ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot schedule exact lunar alarm", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
            )
        }
    }

    fun cancelLunarAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_LUNAR_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, LUNAR_ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Đã hủy lịch báo thức âm lịch")
    }
}
