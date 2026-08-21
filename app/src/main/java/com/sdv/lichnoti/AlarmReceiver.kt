package com.sdv.lichnoti

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "AlarmReceiver"
        const val ACTION_STOP = "com.sdv.lichnoti.ACTION_STOP"
        const val ACTION_SNOOZE = "com.sdv.lichnoti.ACTION_SNOOZE"
        const val ACTION_LUNAR_ALARM = "com.sdv.lichnoti.ACTION_LUNAR_ALARM"
        const val EXTRA_MANUAL_SNOOZE = "manual_snooze"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_STOP -> handleStop(context)
            ACTION_SNOOZE -> handleSnooze(context, intent)
            ACTION_LUNAR_ALARM -> {
                showLunarNotification(context)
                NotificationScheduler.scheduleLunarAlarm(context)
            }
            else -> handleShiftAlarm(context, intent)
        }
    }

    private fun handleStop(context: Context) {
        Log.d(TAG, "Dừng báo thức và lên lịch ca tiếp theo")
        if (AlarmStopPolicy.shouldStopRinging(AlarmStopReason.MANUAL_STOP)) {
            context.stopService(Intent(context, AlarmService::class.java))
        }
        NotificationScheduler.scheduleNext(context)
        if (MdmPendingCoordinator.currentState(context) != null) {
            MdmPendingService.start(context)
        }
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        if (AlarmStopPolicy.shouldStopRinging(AlarmStopReason.MANUAL_SNOOZE)) {
            context.stopService(Intent(context, AlarmService::class.java))
        }
        val prefs = AppPreferences(context)
        val eventId = intent.getStringExtra(MdmPendingCoordinator.EXTRA_EVENT_ID)
        val manual = intent.getBooleanExtra(EXTRA_MANUAL_SNOOZE, true)
        if (manual) {
            MdmPendingCoordinator.cancelForManualSnooze(context, eventId)
        } else if (MdmPendingCoordinator.currentState(context) != null) {
            MdmPendingService.start(context)
        }
        NotificationScheduler.scheduleSnooze(
            context,
            prefs.snoozeDuration,
            retainedMdmEventId = if (manual) null else eventId
        )
    }

    private fun handleShiftAlarm(context: Context, intent: Intent?) {
        val prefs = AppPreferences(context)
        val crewId = prefs.selectedCrew
        val today = LocalDate.now()
        val shiftInfo = ShiftCalculator.getShiftInfo(crewId, today)
        val shouldSkipAlarm = ShiftCalculator.isHoliday(today) && !prefs.holidayAlertEnabled

        val nowTime = java.time.LocalTime.now()
        val isRestDay = shiftInfo.type == ShiftCalculator.ShiftType.NGHI
        val isNightShift = shiftInfo.type == ShiftCalculator.ShiftType.DEM
        val isDaytimeBeforeNightShift = isNightShift && (nowTime.hour < 20)

        if (isRestDay || isDaytimeBeforeNightShift) {
            if (!prefs.offDayAlarmEnabled) {
                NotificationScheduler.scheduleNext(context)
                return
            }
            val yesterday = today.minusDays(1)
            val isAfterNightShift = ShiftCalculator.getActualShift(crewId, yesterday) == ShiftCalculator.ShiftType.DEM
            if (isAfterNightShift && (nowTime.hour < 8 || (nowTime.hour == 8 && nowTime.minute == 0))) {
                NotificationScheduler.scheduleNext(context)
                return
            }
            val dayOfWeekVal = today.dayOfWeek.value
            val activeTimesToday = prefs.getActiveOffDayAlarmTimesForDay(dayOfWeekVal, isNightShiftDay = isDaytimeBeforeNightShift)
            if (activeTimesToday.isEmpty()) {
                NotificationScheduler.scheduleNext(context)
                return
            }
        } else if (shouldSkipAlarm) {
            NotificationScheduler.scheduleNext(context)
            return
        }

        val retainedEventId = intent?.getStringExtra(MdmPendingCoordinator.EXTRA_EVENT_ID)
        val pendingState = if (prefs.autoLockSamsung) {
            MdmPendingCoordinator.begin(context, retainedEventId, "shift_alarm")
        } else {
            MdmPendingCoordinator.cancel(context, "feature_disabled_at_alarm")
            null
        }

        if (prefs.snoozeDuration == 0) {
            NotificationHelper.showNotification(context)
            NotificationScheduler.scheduleNext(context)
            if (pendingState != null) {
                MdmPendingCoordinator.attempt(context, "alarm_without_ui", force = true)
                MdmPendingService.start(context)
            }
            return
        }

        val label = if (isDaytimeBeforeNightShift) "Nghỉ (trước ca Đêm)" else shiftInfo.type.label
        val emoji = if (isDaytimeBeforeNightShift) "😴" else shiftInfo.type.emoji

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_CREW_ID, crewId)
            putExtra(AlarmService.EXTRA_SHIFT_LABEL, label)
            putExtra(AlarmService.EXTRA_SHIFT_EMOJI, emoji)
            pendingState?.eventId?.let {
                putExtra(AlarmService.EXTRA_MDM_EVENT_ID, it)
            }
        }
        try {
            // AlarmService là listener chính trong lúc đang reo; pending service sẽ được
            // khởi động lại khi timeout/Stop nếu MDM vẫn chưa hoàn tất.
            MdmPendingService.stop(context)
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Không thể khởi chạy AlarmService", e)
            // Kèm full-screen intent: dù không có chuông, màn hình khóa vẫn phóng
            // được UI báo thức thay vì chỉ một thông báo nhỏ dễ bị bỏ lỡ.
            NotificationHelper.showNotification(context, fullScreen = true)
            NotificationScheduler.scheduleNext(context)
            if (pendingState != null) {
                MdmPendingCoordinator.attempt(context, "alarm_service_failed", force = true)
                MdmPendingService.start(context)
            }
        }
    }

    private fun showLunarNotification(context: Context) {
        val prefs = AppPreferences(context)
        val mode = prefs.lunarReminderMode
        if (mode == 0) return

        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val lunarToday = VietCalendar.convertSolar2Lunar(today.dayOfMonth, today.monthValue, today.year, 7.0)
        val lunarTomorrow = VietCalendar.convertSolar2Lunar(
            tomorrow.dayOfMonth,
            tomorrow.monthValue,
            tomorrow.year,
            7.0
        )
        val message = when (mode) {
            1 -> when (lunarToday[0]) {
                1 -> "Hôm nay là Mùng 1 âm lịch (1/${lunarToday[1]} âm lịch)"
                15 -> "Hôm nay là ngày Rằm (15/${lunarToday[1]} âm lịch)"
                else -> "Hôm nay là ngày ${lunarToday[0]}/${lunarToday[1]} âm lịch"
            }
            2 -> when (lunarTomorrow[0]) {
                1 -> "Ngày mai là Mùng 1 âm lịch (1/${lunarTomorrow[1]} âm lịch)"
                15 -> "Ngày mai là ngày Rằm (15/${lunarTomorrow[1]} âm lịch)"
                else -> "Ngày mai là ngày âm lịch đặc biệt"
            }
            else -> return
        }

        val channelId = "lunar_reminder_channel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Nhắc nhở ngày Âm", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Thông báo nhắc nhở mùng 1 và 15 Âm lịch"
                }
            )
        }
        val clickIntent = MdmPendingIntents.activityPendingIntent(
            context,
            1002,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
        manager.notify(
            2002,
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("📅 Nhắc nhở ngày Âm lịch")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(clickIntent)
                .build()
        )
    }
}
