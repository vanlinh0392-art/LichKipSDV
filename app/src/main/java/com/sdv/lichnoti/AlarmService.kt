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

        @Volatile
        var isRunning = false
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var audioManager: android.media.AudioManager? = null
    private var originalVolume = -1
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private val autoSnoozeRunnable = Runnable {
        val prefs = AppPreferences(this)
        if (prefs.snoozeDuration == -1) {
            Log.d(TAG, "Báo thức reo quá 2 phút không tương tác -> Tự động dừng hẳn (chế độ Không nhắc lại)")
            if (prefs.autoLockSamsung) {
                SamsungLockHelper.sendLockIntentWithDelay(this)
            }
            sendBroadcastToReceiver(AlarmReceiver.ACTION_STOP)
        } else {
            Log.d(TAG, "Báo thức tự động nhắc lại sau 2 phút không tương tác")
            if (prefs.autoLockSamsung) {
                SamsungLockHelper.sendLockIntentWithDelay(this)
            }
            sendBroadcastToReceiver(AlarmReceiver.ACTION_SNOOZE)
        }
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AlarmService bắt đầu chạy")
        isRunning = true

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

        // Samsung MDM Lock: KHÔNG gọi ở đây (quá sớm, màn hình chưa bật).
        // Intent lock sẽ được gửi từ AlarmActivity foreground khi user bấm DỪNG,
        // hoặc từ AlarmReceiver nếu chế độ snoozeDuration=0 (không có AlarmActivity).
        val prefs = AppPreferences(this)
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
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
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
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (prefs.snoozeDuration != -1) {
            builder.addAction(R.drawable.ic_notification, "NHẮC LẠI", snoozePendingIntent)
        }

        val notification = builder.build()
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
            // 1. Yêu cầu Audio Focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioFocusRequest = focusRequest
                audioManager?.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    { },
                    android.media.AudioManager.STREAM_ALARM,
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }

            // 2. Thiết lập Force Max Volume nếu bật
            val prefs = AppPreferences(this)
            if (prefs.forceMaxVolume) {
                audioManager?.let { am ->
                    originalVolume = am.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
                    val maxVolume = am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                    am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxVolume, 0)
                    Log.d(TAG, "Force Max Volume: Tăng âm lượng báo thức lên tối đa: $maxVolume (Âm lượng cũ: $originalVolume)")
                }
            }

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
        isRunning = false
        handler.removeCallbacks(autoSnoozeRunnable)
        
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null

        // 1. Khôi phục âm lượng ban đầu của hệ thống
        if (originalVolume != -1) {
            try {
                audioManager?.setStreamVolume(android.media.AudioManager.STREAM_ALARM, originalVolume, 0)
                Log.d(TAG, "Đã khôi phục lại âm lượng báo thức ban đầu: $originalVolume")
            } catch (e: Exception) {
                Log.e(TAG, "Không thể khôi phục lại âm lượng báo thức ban đầu", e)
            }
            originalVolume = -1
        }

        // 2. Giải phóng Audio Focus
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus { }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Không thể giải phóng Audio Focus", e)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "AlarmService: onTaskRemoved - App bị đóng từ Recent Apps, dừng báo thức")
        stopSelf()
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
