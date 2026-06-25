package com.sdv.lichnoti

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "AlarmReceiver"
        const val ACTION_STOP = "com.sdv.lichnoti.ACTION_STOP"
        const val ACTION_SNOOZE = "com.sdv.lichnoti.ACTION_SNOOZE"
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
                // Tự động mở app khác nếu được cấu hình
                launchTargetApp(context)
                // Samsung MDM Lock: KHÔNG gọi ở đây.
                // AlarmActivity foreground đã gọi sendLockIntent() khi user bấm DỪNG.
            }
            ACTION_SNOOZE -> {
                Log.d(TAG, "Xử lý hành động NHẮC LẠI báo thức")
                context.stopService(serviceIntent)
                val prefs = AppPreferences(context)
                NotificationScheduler.scheduleSnooze(context, prefs.snoozeDuration)
            }
            else -> {
                Log.d(TAG, "Đến giờ báo thức ca trực")
                val prefs = AppPreferences(context)
                val crewId = prefs.selectedCrew
                val today = java.time.LocalDate.now()
                val shiftInfo = ShiftCalculator.getShiftInfo(crewId, today)

                // Chỉ chạy báo thức nếu kíp có ca làm thực tế (không phải ca nghỉ)
                if (shiftInfo.type != ShiftCalculator.ShiftType.NGHI) {
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
}
