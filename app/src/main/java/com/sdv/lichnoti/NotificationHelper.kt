package com.sdv.lichnoti

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    private const val CHANNEL_ID = "shift_reminder_channel"
    private const val CHANNEL_NAME = "Nhắc nhở ca trực"
    private const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
            description = "Thông báo nhắc nhở dán cam hoặc mở app MDM"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes)
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * @param fullScreen true = gắn thêm full-screen intent mở AlarmActivity (dùng cho
     * nhánh fallback khi AlarmService không khởi động được — màn hình khóa vẫn hiện
     * được UI báo thức dù không có chuông). KHÔNG bật cho chế độ "Không báo thức".
     */
    fun showNotification(context: Context, fullScreen: Boolean = false) {
        val prefs = AppPreferences(context)
        val crewId = prefs.selectedCrew
        val crewName = ShiftCalculator.CREWS.find { it.id == crewId }?.name ?: crewId

        val today = java.time.LocalDate.now()
        val shiftInfo = ShiftCalculator.getShiftInfo(crewId, today)
        
        val nowTime = java.time.LocalTime.now()
        val isDaytimeBeforeNight = (shiftInfo.type == ShiftCalculator.ShiftType.DEM) && (nowTime.hour < 20)
        val isOfficialHol = ShiftCalculator.isHoliday(today)
        val shiftLabel = if (isDaytimeBeforeNight) {
            "😴 Nghỉ (trước ca Đêm)"
        } else if (isOfficialHol) {
            val label = when (shiftInfo.type) {
                ShiftCalculator.ShiftType.NGAY -> "HO Ngày"
                ShiftCalculator.ShiftType.DEM -> "HO Đêm"
                else -> "Nghỉ lễ"
            }
            "🎉 ${shiftInfo.holidayName} ($label)"
        } else if (shiftInfo.isHoliday) {
            "${shiftInfo.type.emoji} Ca ${shiftInfo.type.label} (HO)"
        } else {
            "${shiftInfo.type.emoji} Ca ${shiftInfo.type.label}"
        }

        val pendingState = MdmPendingCoordinator.currentState(context)
        val pendingIntent = if (
            prefs.autoLockSamsung &&
            pendingState != null &&
            SamsungLockHelper.isVSelfLockTargetAvailable(context)
        ) {
            MdmPendingIntents.bridgePendingIntent(
                context,
                pendingState.eventId,
                "reminder_notification"
            )
        } else {
            MdmPendingIntents.activityPendingIntent(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
        }

        val customContent = prefs.notificationContent.ifBlank { "Hãy dán cam hoặc mở app MDM" }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ Nhắc nhở $crewName - $shiftLabel")
            .setContentText(customContent)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$customContent\n$crewName đang làm ca $shiftLabel"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))

        val notificationManager = context.getSystemService(NotificationManager::class.java)

        if (fullScreen) {
            val label = if (isDaytimeBeforeNight) "Nghỉ (trước ca Đêm)" else shiftInfo.type.label
            val emoji = if (isDaytimeBeforeNight) "😴" else shiftInfo.type.emoji
            val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
                putExtra(AlarmService.EXTRA_CREW_ID, crewId)
                putExtra(AlarmService.EXTRA_SHIFT_LABEL, label)
                putExtra(AlarmService.EXTRA_SHIFT_EMOJI, emoji)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val fullScreenPendingIntent =
                MdmPendingIntents.activityPendingIntent(context, 3, alarmIntent)
            val canUseFullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                notificationManager.canUseFullScreenIntent()
            if (canUseFullScreen) {
                builder.setFullScreenIntent(fullScreenPendingIntent, true)
                builder.setCategory(NotificationCompat.CATEGORY_ALARM)
            }
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
}
