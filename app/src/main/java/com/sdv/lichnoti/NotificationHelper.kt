package com.sdv.lichnoti

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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

    fun showNotification(context: Context) {
        val prefs = AppPreferences(context)
        val crewId = prefs.selectedCrew
        val crewName = ShiftCalculator.CREWS.find { it.id == crewId }?.name ?: crewId

        val today = java.time.LocalDate.now()
        val shiftInfo = ShiftCalculator.getShiftInfo(crewId, today)
        
        val isOfficialHol = ShiftCalculator.isHoliday(today)
        val shiftLabel = if (isOfficialHol) {
            val label = if (shiftInfo.type == ShiftCalculator.ShiftType.NGAY) "HO Ngày" else "HO Đêm"
            "🎉 ${shiftInfo.holidayName} ($label)"
        } else if (shiftInfo.isHoliday) {
            "${shiftInfo.type.emoji} Ca ${shiftInfo.type.label} (HO)"
        } else {
            "${shiftInfo.type.emoji} Ca ${shiftInfo.type.label}"
        }

        // Create intent based on user preference
        val intent = if (prefs.openSelf || prefs.targetPackage.isBlank()) {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            context.packageManager.getLaunchIntentForPackage(prefs.targetPackage)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            } ?: Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val customContent = prefs.notificationContent.ifBlank { "Hãy dán cam hoặc mở app MDM" }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
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
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
