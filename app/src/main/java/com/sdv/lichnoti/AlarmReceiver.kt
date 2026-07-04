package com.sdv.lichnoti

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "AlarmReceiver"
        const val ACTION_STOP = "com.sdv.lichnoti.ACTION_STOP"
        const val ACTION_SNOOZE = "com.sdv.lichnoti.ACTION_SNOOZE"
        const val ACTION_LUNAR_ALARM = "com.sdv.lichnoti.ACTION_LUNAR_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d(TAG, "Nhận broadcast action: $action")

        val serviceIntent = Intent(context, AlarmService::class.java)

        when (action) {
            ACTION_STOP -> {
                Log.d(TAG, "Xử lý hành động DỪNG báo thức")
                context.stopService(serviceIntent)
                NotificationScheduler.scheduleNext(context)
                // Không gọi launchTargetApp() ở đây — AlarmActivity foreground đã xử lý mở app/MDM lock rồi.
                // Gọi từ background sẽ gây duplicate launch (VSelfLock mở rồi bị destroy).
            }
            ACTION_SNOOZE -> {
                Log.d(TAG, "Xử lý hành động NHẮC LẠI báo thức")
                context.stopService(serviceIntent)
                val prefs = AppPreferences(context)
                NotificationScheduler.scheduleSnooze(context, prefs.snoozeDuration)
            }
            ACTION_LUNAR_ALARM -> {
                Log.d(TAG, "Xử lý báo thức âm lịch mùng 1 & 15")
                showLunarNotification(context)
                NotificationScheduler.scheduleLunarAlarm(context)
            }
            else -> {
                Log.d(TAG, "Đến giờ báo thức ca trực")
                val prefs = AppPreferences(context)
                val crewId = prefs.selectedCrew
                val today = java.time.LocalDate.now()
                val shiftInfo = ShiftCalculator.getShiftInfo(crewId, today)

                val isHoliday = ShiftCalculator.isHoliday(today)
                val shouldSkipAlarm = isHoliday && !prefs.holidayAlertEnabled

                // Chỉ chạy báo thức nếu kíp có ca làm thực tế (không phải ca nghỉ) và không cấu hình bỏ qua ngày Lễ
                if (shiftInfo.type != ShiftCalculator.ShiftType.NGHI && !shouldSkipAlarm) {
                    // ĐÁNH CHẶN KHI MÀN HÌNH ĐANG MỞ: Mở thẳng MDM mà không phát chuông reo
                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    val hasOverlay = Settings.canDrawOverlays(context)
                    Log.d(TAG, "Đánh chặn kiểm tra: autoSendMdmOnScreen=${prefs.autoSendMdmOnScreen}, autoLockSamsung=${prefs.autoLockSamsung}, hasOverlay=$hasOverlay, isInteractive=${pm.isInteractive}")
                    if (prefs.autoSendMdmOnScreen && prefs.autoLockSamsung && hasOverlay && pm.isInteractive) {
                        Log.d(TAG, "Màn hình đang mở -> Kích hoạt thẳng MDM không phát chuông báo thức")
                        NotificationScheduler.scheduleNext(context)
                        SamsungLockHelper.resetDebounce()
                        val success = SamsungLockHelper.sendLockIntent(context)
                        if (success) {
                            Log.d(TAG, "Gửi MDM thành công trực tiếp từ Receiver, kết thúc luồng")
                            // Rung phản hồi nhẹ (Haptic Feedback)
                            try {
                                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(150)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Không thể rung phản hồi: ${e.message}")
                            }
                            // Hiển thị Toast thông báo visual
                            android.widget.Toast.makeText(context, "⏰ [Lịch Kíp] Tự động on MDM & Lên lịch ca tiếp theo", android.widget.Toast.LENGTH_SHORT).show()
                            return
                        }
                    }

                    if (prefs.snoozeDuration == 0) {
                        // Người dùng cấu hình "Không" sử dụng báo thức full màn reo chuông
                        // Chỉ hiển thị notification nhắc nhở dán cam thông thường và lên lịch ca tiếp theo
                        NotificationHelper.showNotification(context)
                        NotificationScheduler.scheduleNext(context)
                        // Samsung MDM Lock — chế độ không có AlarmActivity (snoozeDuration=0)
                        // Gọi từ background với delay + retry vì One UI chặn background activity start
                        if (prefs.autoLockSamsung) {
                            SamsungLockHelper.sendLockIntentWithDelay(context)
                        }
                    } else {
                        serviceIntent.apply {
                            putExtra(AlarmService.EXTRA_CREW_ID, crewId)
                            putExtra(AlarmService.EXTRA_SHIFT_LABEL, shiftInfo.type.label)
                            putExtra(AlarmService.EXTRA_SHIFT_EMOJI, shiftInfo.type.emoji)
                        }
                        try {
                            ContextCompat.startForegroundService(context, serviceIntent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Không thể khởi chạy Foreground Service báo thức", e)
                            // Fallback hiển thị notification nhắc nhở thông thường nếu crash
                            NotificationHelper.showNotification(context)
                            NotificationScheduler.scheduleNext(context)
                        }
                    }
                } else {
                    NotificationScheduler.scheduleNext(context)
                }
            }
        }
    }

    private fun launchTargetApp(context: Context) {
        val prefs = AppPreferences(context)
        if (!prefs.openSelf && prefs.targetPackage.isNotBlank()) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(prefs.targetPackage)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    Log.d(TAG, "Đã tự động mở ứng dụng mục tiêu: ${prefs.targetPackage}")
                } else {
                    Log.e(TAG, "Không tìm thấy launch intent cho package: ${prefs.targetPackage}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Không thể tự động mở ứng dụng mục tiêu", e)
            }
        }
    }

    private fun showLunarNotification(context: Context) {
        val prefs = AppPreferences(context)
        val mode = prefs.lunarReminderMode
        if (mode == 0) return

        val today = java.time.LocalDate.now()
        val tomorrow = today.plusDays(1)

        val lunarToday = VietCalendar.convertSolar2Lunar(today.dayOfMonth, today.monthValue, today.year, 7.0)
        val lunarTom = VietCalendar.convertSolar2Lunar(tomorrow.dayOfMonth, tomorrow.monthValue, tomorrow.year, 7.0)

        val title = "📅 Nhắc nhở ngày Âm lịch"
        var msg = ""

        if (mode == 1) { // Báo cùng ngày
            msg = if (lunarToday[0] == 1) {
                "Hôm nay là Mùng 1 âm lịch (1/${lunarToday[1]} âm lịch)"
            } else if (lunarToday[0] == 15) {
                "Hôm nay là ngày Rằm (15/${lunarToday[1]} âm lịch)"
            } else {
                "Hôm nay là ngày ${lunarToday[0]}/${lunarToday[1]} âm lịch"
            }
        } else if (mode == 2) { // Trước 1 ngày
            msg = if (lunarTom[0] == 1) {
                "Ngày mai là Mùng 1 âm lịch (1/${lunarTom[1]} âm lịch)"
            } else if (lunarTom[0] == 15) {
                "Ngày mai là ngày Rằm (15/${lunarTom[1]} âm lịch)"
            } else {
                "Ngày mai là ngày âm lịch đặc biệt"
            }
        }

        if (msg.isBlank()) return

        // Gửi Notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "lunar_reminder_channel"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(channelId) == null) {
                val channel = android.app.NotificationChannel(
                    channelId, "Nhắc nhở ngày Âm",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Thông báo nhắc nhở mùng 1 & 15 Âm lịch"
                }
                nm.createNotificationChannel(channel)
            }
        }

        // Intent mở app Lịch SDV khi click
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1002, clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(msg)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(2002, notification)
    }
}
