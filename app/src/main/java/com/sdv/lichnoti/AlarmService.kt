package com.sdv.lichnoti

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    companion object {
        private const val TAG = "AlarmService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "alarm_service_channel"
        private const val CHANNEL_NAME = "Báo thức ca trực"
        
        const val EXTRA_CREW_ID = "crew_id"
        const val EXTRA_SHIFT_LABEL = "shift_label"
        const val EXTRA_SHIFT_EMOJI = "shift_emoji"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoSnoozeRunnable = Runnable {
        Log.d(TAG, "Báo thức tự động nhắc lại sau 2 phút không tương tác")
        sendBroadcastToReceiver(AlarmReceiver.ACTION_SNOOZE)
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AlarmService bắt đầu chạy")

        // Đánh thức CPU và bật sáng màn hình ngay khi chuông reo (Hỗ trợ tốt cho Xiaomi/Samsung)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "LichNoti:AlarmWakeLock"
        )
        wakeLock.acquire(15 * 1000L) // Giữ sáng màn hình 15 giây

        val crewId = intent?.getStringExtra(EXTRA_CREW_ID) ?: "A"
        val shiftLabel = intent?.getStringExtra(EXTRA_SHIFT_LABEL) ?: "Ngày"
        val shiftEmoji = intent?.getStringExtra(EXTRA_SHIFT_EMOJI) ?: "☀️"

        val prefs = AppPreferences(this)
        if (prefs.autoLockSamsung && Settings.canDrawOverlays(this)) {
            SamsungLockHelper.sendLockIntent(this)
        }
        val crewName = ShiftCalculator.CREWS.find { it.id == crewId }?.name ?: crewId
        val msg = prefs.notificationContent

        // 1. Tạo Intent mở AlarmActivity (Full Screen)
        val alarmIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(EXTRA_CREW_ID, crewId)
            putExtra(EXTRA_SHIFT_LABEL, shiftLabel)
            putExtra(EXTRA_SHIFT_EMOJI, shiftEmoji)
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Tạo action Dừng báo thức trên Notification bằng PendingIntent.getActivity để mở AlarmActivity và kích hoạt lock ở foreground
        val stopIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(EXTRA_CREW_ID, crewId)
            putExtra(EXTRA_SHIFT_LABEL, shiftLabel)
            putExtra(EXTRA_SHIFT_EMOJI, shiftEmoji)
            putExtra("EXTRA_AUTO_STOP_AND_LOCK", true)
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val stopPendingIntent = PendingIntent.getActivity(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Tạo action Nhắc lại trên Notification
        val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_SNOOZE
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this, 2, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4. Tạo Notification Full Screen
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ Nhắc nhở $crewName - Ca $shiftLabel $shiftEmoji")
            .setContentText(msg)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(R.drawable.ic_notification, "DỪNG", stopPendingIntent)
            .addAction(R.drawable.ic_notification, "NHẮC LẠI", snoozePendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // 5. Phát nhạc chuông báo thức
        playRingtone()

        // 6. Rung thiết bị
        startVibrator()

        // 7. Hẹn giờ tự động snooze sau 2 phút
        handler.removeCallbacks(autoSnoozeRunnable)
        handler.postDelayed(autoSnoozeRunnable, 120_000) // 2 phút

        return START_NOT_STICKY
    }

    private fun playRingtone() {
        try {
            var alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alert == null) {
                alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                if (alert == null) {
                    alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
            }
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alert)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                setAudioAttributes(audioAttributes)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Không thể phát nhạc chuông", e)
        }
    }

    private fun startVibrator() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 800, 800, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun sendBroadcastToReceiver(actionStr: String) {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = actionStr
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        Log.d(TAG, "AlarmService dừng và giải phóng tài nguyên")
        handler.removeCallbacks(autoSnoozeRunnable)
        
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Kênh phục vụ báo thức toàn màn hình"
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
