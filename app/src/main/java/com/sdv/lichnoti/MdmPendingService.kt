package com.sdv.lichnoti

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class MdmPendingService : Service() {
    companion object {
        private const val TAG = "MdmPendingService"
        private const val CHANNEL_ID = "mdm_pending_channel"
        private const val NOTIFICATION_ID = 2102

        fun start(context: Context) {
            if (MdmPendingStore.load(context) == null) return
            try {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context, MdmPendingService::class.java)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Không thể khởi động pending foreground service", e)
            }
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(Intent(context, MdmPendingService::class.java))
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false
    private val expiryRunnable = Runnable { MdmPendingCoordinator.expire(this, "service_timer") }
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT ->
                    MdmPendingCoordinator.attempt(context, "user_present", force = true)
                Intent.ACTION_SCREEN_ON ->
                    MdmPendingCoordinator.attempt(context, "screen_on", force = true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        // USER_PRESENT / SCREEN_ON là protected system broadcast — vẫn nhận được với
        // NOT_EXPORTED; không cần mở receiver cho app ngoài.
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = MdmPendingStore.load(this)

        // LUÔN startForeground() TRƯỚC TIÊN: service này được khởi động bằng
        // startForegroundService(), nếu stopSelf() mà chưa từng vào foreground thì hệ thống
        // sẽ crash app với ForegroundServiceDidNotStartInTimeException (Android 8–15).
        val notification = state?.let { buildNotification(it) } ?: buildIdleNotification()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)

        if (state == null || MdmPendingPolicy.isExpired(state, System.currentTimeMillis())) {
            if (state != null) MdmPendingCoordinator.expire(this, "service_start")
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        MdmRetryScheduler.scheduleNext(this, state)

        handler.removeCallbacks(expiryRunnable)
        handler.postDelayed(expiryRunnable, (state.expiresAtMs - System.currentTimeMillis()).coerceAtLeast(1L))
        if (MdmDeviceState.isUnlockedAndInteractive(this)) {
            handler.post { MdmPendingCoordinator.attempt(this, "pending_service_start") }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(expiryRunnable)
        if (receiverRegistered) {
            runCatching { unregisterReceiver(unlockReceiver) }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Notification tối thiểu khi service bị start mà không còn state — chỉ tồn tại
     *  vài mili giây trước khi stopForeground(REMOVE), nhưng bắt buộc phải có để
     *  thỏa hợp đồng startForegroundService(). */
    private fun buildIdleNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Đang dừng tác vụ MDM")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun buildNotification(state: PendingMdmState): Notification {
        val retryIntent = MdmPendingIntents.bridgePendingIntent(this, state.eventId, "notification_retry")
        val settingsIntent = MdmPendingIntents.activityPendingIntent(
            this,
            2104,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Đang chờ on MDM")
            .setContentText("Sẽ tự thử lại khi bạn mở khóa màn hình")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(retryIntent)
            .addAction(R.drawable.ic_notification, "THỬ LẠI", retryIntent)
            .addAction(R.drawable.ic_notification, "CÀI ĐẶT", settingsIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "MDM đang chờ", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Giữ yêu cầu on MDM cho đến khi thiết bị được mở khóa"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }
}
