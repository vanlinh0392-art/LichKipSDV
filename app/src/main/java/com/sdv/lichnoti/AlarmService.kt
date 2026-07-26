package com.sdv.lichnoti

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class AlarmService : Service() {

    companion object {
        private const val TAG = "AlarmService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "alarm_service_channel"
        private const val CHANNEL_NAME = "Báo thức ca trực"

        const val EXTRA_CREW_ID = "crew_id"
        const val EXTRA_SHIFT_LABEL = "shift_label"
        const val EXTRA_SHIFT_EMOJI = "shift_emoji"
        const val EXTRA_MDM_EVENT_ID = "mdm_event_id"

        @Volatile
        var isRunning = false
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var audioManager: android.media.AudioManager? = null
    private var originalVolume = -1
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private var currentEventId: String? = null
    private var receiverRegistered = false

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (currentEventId == null) return
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT ->
                    MdmPendingCoordinator.attempt(context, "alarm_user_present", force = true)
                Intent.ACTION_SCREEN_ON ->
                    MdmPendingCoordinator.attempt(context, "alarm_screen_on", force = true)
            }
        }
    }

    private val autoSnoozeRunnable = Runnable {
        val prefs = AppPreferences(this)
        if (currentEventId != null && prefs.autoLockSamsung) {
            MdmPendingCoordinator.attempt(this, "auto_timeout", force = true)
            MdmPendingService.start(this)
        }
        stopRingingResources()
        if (prefs.snoozeDuration == -1) {
            sendBroadcastToReceiver(AlarmReceiver.ACTION_STOP, manualSnooze = false)
        } else {
            sendBroadcastToReceiver(AlarmReceiver.ACTION_SNOOZE, manualSnooze = false)
        }
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        createNotificationChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        val prefs = AppPreferences(this)
        currentEventId = intent?.getStringExtra(EXTRA_MDM_EVENT_ID)
            ?: MdmPendingCoordinator.currentState(this)?.eventId
            ?: MdmPendingCoordinator.begin(this, origin = "alarm_service")?.eventId

        wakeScreen()

        val crewId = intent?.getStringExtra(EXTRA_CREW_ID) ?: "A"
        val shiftLabel = intent?.getStringExtra(EXTRA_SHIFT_LABEL) ?: "Ngày"
        val shiftEmoji = intent?.getStringExtra(EXTRA_SHIFT_EMOJI) ?: "☀️"
        val crewName = ShiftCalculator.CREWS.find { it.id == crewId }?.name ?: crewId

        val alarmIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(EXTRA_CREW_ID, crewId)
            putExtra(EXTRA_SHIFT_LABEL, shiftLabel)
            putExtra(EXTRA_SHIFT_EMOJI, shiftEmoji)
            currentEventId?.let { putExtra(EXTRA_MDM_EVENT_ID, it) }
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = MdmPendingIntents.activityPendingIntent(this, 0, alarmIntent)

        val stopIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(EXTRA_CREW_ID, crewId)
            putExtra(EXTRA_SHIFT_LABEL, shiftLabel)
            putExtra(EXTRA_SHIFT_EMOJI, shiftEmoji)
            putExtra("EXTRA_AUTO_STOP_AND_LOCK", true)
            currentEventId?.let { putExtra(EXTRA_MDM_EVENT_ID, it) }
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val stopPendingIntent = MdmPendingIntents.activityPendingIntent(this, 1, stopIntent)

        val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_SNOOZE
            putExtra(AlarmReceiver.EXTRA_MANUAL_SNOOZE, true)
            currentEventId?.let { putExtra(MdmPendingCoordinator.EXTRA_EVENT_ID, it) }
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this,
            2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ Nhắc nhở $crewName - Ca $shiftLabel $shiftEmoji")
            .setContentText(prefs.notificationContent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(R.drawable.ic_notification, "DỪNG", stopPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val notificationManager = getSystemService(NotificationManager::class.java)
        val canUseFullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()
        if (canUseFullScreen) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        } else {
            Log.w(TAG, "Quyền full-screen intent đang tắt; dùng heads-up/content intent")
        }
        if (prefs.snoozeDuration != -1) {
            builder.addAction(R.drawable.ic_notification, "NHẮC LẠI", snoozePendingIntent)
        }

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, builder.build(), serviceType)
        playRingtone()
        startVibrator()
        handler.removeCallbacks(autoSnoozeRunnable)
        handler.postDelayed(autoSnoozeRunnable, 120_000L)
        return START_NOT_STICKY
    }

    private fun wakeScreen() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "LichNoti:AlarmWakeLock"
        ).acquire(15_000L)
    }

    private fun playRingtone() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = android.media.AudioFocusRequest.Builder(
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                ).setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                ).setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioFocusRequest = request
                audioManager?.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    { },
                    android.media.AudioManager.STREAM_ALARM,
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }

            audioManager?.let { manager ->
                originalVolume = manager.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
                manager.setStreamVolume(
                    android.media.AudioManager.STREAM_ALARM,
                    manager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM),
                    0
                )
            }

            val alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alert)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Không thể phát nhạc chuông", e)
        }
    }

    private fun startVibrator() {
        vibrator = getSystemService(Vibrator::class.java)
        val pattern = longArrayOf(0, 800, 800, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun sendBroadcastToReceiver(action: String, manualSnooze: Boolean) {
        sendBroadcast(Intent(this, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra(AlarmReceiver.EXTRA_MANUAL_SNOOZE, manualSnooze)
            currentEventId?.let { putExtra(MdmPendingCoordinator.EXTRA_EVENT_ID, it) }
        })
    }

    private fun stopRingingResources() {
        handler.removeCallbacks(autoSnoozeRunnable)
        runCatching { mediaPlayer?.stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        isRunning = false
        stopRingingResources()
        if (originalVolume != -1) {
            runCatching {
                audioManager?.setStreamVolume(
                    android.media.AudioManager.STREAM_ALARM,
                    originalVolume,
                    0
                )
            }
            originalVolume = -1
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus { }
            }
        }
        if (receiverRegistered) {
            runCatching { unregisterReceiver(unlockReceiver) }
            receiverRegistered = false
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (MdmPendingCoordinator.currentState(this) != null) {
            MdmPendingService.start(this)
        }
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Kênh báo thức toàn màn hình"
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }
}
