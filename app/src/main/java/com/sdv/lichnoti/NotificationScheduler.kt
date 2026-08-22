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
    internal const val ALARM_REQUEST_CODE = 2001
    internal const val SNOOZE_REQUEST_CODE = 2003
    private const val ACTION_SHIFT_ALARM_TRIGGER = "com.sdv.lichnoti.ACTION_SHIFT_ALARM_TRIGGER"
    private const val ACTION_SNOOZE_ALARM_TRIGGER = "com.sdv.lichnoti.ACTION_SNOOZE_ALARM_TRIGGER"

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
                    val candidates = mutableListOf<LocalDateTime>()

                    val nightAlarm = date.atTime(prefs.nightNotificationHour, prefs.nightNotificationMinute)
                    if (nightAlarm.isAfter(now)) {
                        candidates.add(nightAlarm)
                    }

                    if (prefs.offDayAlarmEnabled) {
                        val yesterday = date.minusDays(1)
                        val isAfterNightShift = ShiftCalculator.getActualShift(crewId, yesterday) == ShiftCalculator.ShiftType.DEM
                        val nightAlarmTime = java.time.LocalTime.of(prefs.nightNotificationHour, prefs.nightNotificationMinute)

                        val validOffTimes = prefs.getActiveOffDayAlarmTimesForDay(date.dayOfWeek.value, isNightShiftDay = true).mapNotNull { timeStr ->
                            val parts = timeStr.split(":")
                            if (parts.size != 2) return@mapNotNull null
                            val h = parts[0].toIntOrNull() ?: return@mapNotNull null
                            val m = parts[1].toIntOrNull() ?: return@mapNotNull null
                            val offTime = java.time.LocalTime.of(h, m)

                            // Chỉ nhận các mốc trước giờ báo ca Đêm và trước 20:00 (để ca Đêm luôn ưu tiên cao nhất)
                            if (h >= 20 || !offTime.isBefore(nightAlarmTime)) return@mapNotNull null

                            // Nếu hôm qua là ca đêm thì bỏ qua các mốc trong ca làm việc đêm (<= 08:00 sáng)
                            if (isAfterNightShift && (h < 8 || (h == 8 && m == 0))) {
                                return@mapNotNull null
                            }
                            date.atTime(h, m)
                        }.filter { it.isAfter(now) }

                        candidates.addAll(validOffTimes)
                    }

                    val earliest = candidates.minOrNull()
                    if (earliest != null) {
                        return earliest
                    }
                }
                ShiftCalculator.ShiftType.NGHI -> {
                    if (prefs.offDayAlarmEnabled) {
                        val yesterday = date.minusDays(1)
                        val isAfterNightShift = ShiftCalculator.getActualShift(crewId, yesterday) == ShiftCalculator.ShiftType.DEM

                        val validTimes = prefs.getActiveOffDayAlarmTimesForDay(date.dayOfWeek.value).mapNotNull { timeStr ->
                            val parts = timeStr.split(":")
                            if (parts.size != 2) return@mapNotNull null
                            val h = parts[0].toIntOrNull() ?: return@mapNotNull null
                            val m = parts[1].toIntOrNull() ?: return@mapNotNull null

                            // Không báo thức trong thời gian làm việc ca đêm sát ngày nghỉ (20h - 8h sáng hôm sau)
                            if (isAfterNightShift && (h < 8 || (h == 8 && m == 0))) {
                                return@mapNotNull null
                            }
                            date.atTime(h, m)
                        }.filter { it.isAfter(now) }.sorted()

                        if (validTimes.isNotEmpty()) {
                            return validTimes.first()
                        }
                    }
                }
            }
        }
        return null
    }

    private fun setExactAlarm(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        // Remove the pre-v4.67 identity (same requestCode but no action) after an upgrade.
        alarmManager.cancel(legacyShiftPendingIntent(context))
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SHIFT_ALARM_TRIGGER
        }
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
        val shiftIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SHIFT_ALARM_TRIGGER
        }
        val shiftPendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, shiftIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALARM_TRIGGER
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, SNOOZE_REQUEST_CODE, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(shiftPendingIntent)
        alarmManager.cancel(snoozePendingIntent)
        alarmManager.cancel(legacyShiftPendingIntent(context))
    }

    fun scheduleSnooze(context: Context, minutes: Int, retainedMdmEventId: String? = null) {
        val triggerAtMillis = System.currentTimeMillis() + minutes * 60 * 1000
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALARM_TRIGGER
            retainedMdmEventId?.let {
                putExtra(MdmPendingCoordinator.EXTRA_EVENT_ID, it)
            }
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, SNOOZE_REQUEST_CODE, intent,
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

    private fun legacyShiftPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
