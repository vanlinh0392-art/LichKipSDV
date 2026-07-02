package com.sdv.lichnoti

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * SamsungLockHelper — Hệ thống gửi Intent kích hoạt khóa MDM (VSelfLock) cho Samsung One UI.
 *
 * ===== KIẾN TRÚC FALLBACK NHIỀU LỚP =====
 *
 * [Foreground path — sendLockIntent()]
 *   → Gọi trực tiếp từ Activity đang hiển thị (AlarmActivity, autoLockRunnable)
 *   → startActivity() luôn được phép, không cần fallback.
 *
 * [Background path — sendLockIntentWithDelay()]
 *   Được gọi khi không có Activity ở foreground (snoozeDuration=0, auto-snooze).
 *   Hệ thống One UI chặn startActivity từ background → cần fallback nhiều lớp:
 *
 *   Layer 1 (Ngay lập tức): Full-Screen Notification Intent
 *     → Gửi notification với setFullScreenIntent() + PRIORITY_MAX + CATEGORY_ALARM
 *     → Hệ thống One UI tự động kích hoạt Activity kể cả khi màn hình tắt/khóa
 *     → Dùng kênh riêng "mdm_lock_channel" để tránh bị ảnh hưởng bởi kênh thường
 *
 *   Layer 2 (Sau 1.5s): WakeLock + startActivity trực tiếp
 *     → Đánh thức màn hình trước bằng WakeLock SCREEN_BRIGHT + ACQUIRE_CAUSES_WAKEUP
 *     → Sau khi màn hình sáng, gửi startActivity
 *     → Cờ FLAG_ACTIVITY_NEW_TASK bắt buộc cho background context
 *
 *   Layer 3 (Sau 4s): Retry lần 2 startActivity
 *     → Phòng hờ trường hợp Layer 2 bị hệ thống chặn tạm (doze, power save)
 *
 * ===== LƯU Ý VỀ INTENT DUPLICATE =====
 *   - Biến `lockSentTimestamp` kiểm soát chống gửi trùng trong vòng 10 giây.
 *   - Chỉ Layer 1 (Full-Screen Notification) chạy ngay, Layer 2+3 là fallback thực sự.
 *   - Layer 2 không chạy nếu Full-Screen Intent đã kích hoạt thành công Activity.
 *
 * ===== INTENT FLAGS CHO VSELFLOCK =====
 *   - FLAG_ACTIVITY_NEW_TASK: bắt buộc khi gửi từ non-Activity context
 *   - FLAG_ACTIVITY_CLEAR_TOP: không tạo instance mới nếu đang chạy, gọi onNewIntent
 *   - KHÔNG dùng FLAG_ACTIVITY_SINGLE_TOP: tránh trường hợp onNewIntent bị bỏ qua
 *   - FLAG_ACTIVITY_NO_ANIMATION + EXCLUDE_FROM_RECENTS: ẩn khỏi Recent Apps
 */
object SamsungLockHelper {
    private const val TAG = "SamsungLockHelper"
    private const val VSELFLOCK_PACKAGE = "com.samsung.s1.vselflock"
    private const val VSELFLOCK_ACTIVITY = "com.samsung.s1.vselflock.ui.MainActivity"

    // Kênh notification riêng cho MDM Lock (độc lập với kênh nhắc nhở thường)
    private const val MDM_CHANNEL_ID = "mdm_lock_channel"
    private const val MDM_CHANNEL_NAME = "MDM Auto Lock"
    private const val MDM_NOTIFICATION_ID = 2001

    // Chống gửi trùng: không gửi lại trong vòng 10 giây
    private const val DEBOUNCE_MS = 10_000L
    private var lastLockSentMs = 0L

    // Delay các lớp fallback
    private const val LAYER2_DELAY_MS = 1_500L  // chờ WakeLock đánh thức màn hình
    private const val LAYER3_DELAY_MS = 4_000L  // retry cuối

    private val handler = Handler(Looper.getMainLooper())

    // =========================================================
    // PUBLIC API
    // =========================================================

    /**
     * Gửi lock intent NGAY LẬP TỨC từ foreground context (Activity đang hiển thị).
     * Không cần fallback, One UI luôn cho phép startActivity từ foreground.
     */
    fun sendLockIntent(context: Context): Boolean {
        if (!isVSelfLockInstalled(context)) {
            Log.w(TAG, "VSelfLock chưa cài — bỏ qua")
            return false
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        Log.d(TAG, "[FOREGROUND] Gửi lock intent | isInteractive=${pm.isInteractive} | caller=${context.javaClass.simpleName}")

        markSent()
        return try {
            context.startActivity(buildLockIntent())
            Log.d(TAG, "[FOREGROUND] ✅ startActivity thành công")
            true
        } catch (e: Exception) {
            Log.e(TAG, "[FOREGROUND] ❌ startActivity thất bại: ${e.message}", e)
            false
        }
    }

    /**
     * Gửi lock intent từ background context (BroadcastReceiver, Service).
     * Áp dụng hệ thống fallback 3 lớp để đảm bảo hoạt động ổn định trên One UI.
     */
    fun sendLockIntentWithDelay(context: Context) {
        if (!isVSelfLockInstalled(context)) {
            Log.w(TAG, "VSelfLock chưa cài — bỏ qua")
            return
        }

        // Chống gửi trùng
        val now = System.currentTimeMillis()
        if (now - lastLockSentMs < DEBOUNCE_MS) {
            Log.d(TAG, "[BACKGROUND] Bỏ qua — đã gửi cách đây ${now - lastLockSentMs}ms (debounce ${DEBOUNCE_MS}ms)")
            return
        }

        val appContext = context.applicationContext
        Log.d(TAG, "[BACKGROUND] Bắt đầu chuỗi fallback | caller=${context.javaClass.simpleName}")

        markSent()

        // === LAYER 1: Full-Screen Intent qua Notification (ngay lập tức) ===
        val layer1Success = sendFullScreenNotification(appContext)
        Log.d(TAG, "[LAYER 1] Full-Screen Notification: ${if (layer1Success) "✅ Gửi thành công" else "❌ Thất bại"}")

        // Tự động hủy thông báo sau 3.5 giây đề phòng trường hợp MDM đã được mở thành công ở Layer 1
        handler.postDelayed({
            cancelMdmNotification(appContext)
        }, 3500L)

        // === LAYER 2: WakeLock + startActivity trực tiếp (sau 1.5s) ===
        // Chạy song song như lưới bảo vệ — One UI có thể bỏ qua Full-Screen Notification
        // trong chế độ tiết kiệm pin hoặc khi thông báo bị im lặng
        wakeScreenIfNeeded(appContext)
        handler.postDelayed({
            Log.d(TAG, "[LAYER 2] Thử startActivity trực tiếp sau ${LAYER2_DELAY_MS}ms")
            val success = tryStartActivity(appContext, "layer2")
            if (success) {
                cancelMdmNotification(appContext)
            } else {
                // === LAYER 3: Retry lần cuối sau 4s ===
                handler.postDelayed({
                    Log.d(TAG, "[LAYER 3] Retry startActivity lần cuối sau ${LAYER3_DELAY_MS}ms")
                    val success3 = tryStartActivity(appContext, "layer3_retry")
                    if (success3) {
                        cancelMdmNotification(appContext)
                    }
                }, LAYER3_DELAY_MS - LAYER2_DELAY_MS)
            }
        }, LAYER2_DELAY_MS)
    }

    /**
     * Xóa trạng thái debounce — dùng khi cần gửi lại ngay (ví dụ: user bấm nút thủ công).
     */
    fun resetDebounce() {
        lastLockSentMs = 0L
    }

    /**
     * Kiểm tra xem lệnh kích hoạt MDM có vừa mới được gửi đi trong vòng 4 giây qua hay không.
     */
    fun isLockJustSent(): Boolean {
        return System.currentTimeMillis() - lastLockSentMs < 4000L
    }

    fun isVSelfLockInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(VSELFLOCK_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    // =========================================================
    // PRIVATE HELPERS
    // =========================================================

    /**
     * Layer 1: Gửi Full-Screen Intent qua Notification.
     * Kênh riêng biệt với IMPORTANCE_HIGH + lockscreenVisibility PUBLIC.
     */
    private fun sendFullScreenNotification(context: Context): Boolean {
        return try {
            ensureMdmChannel(context)

            val lockIntent = buildLockIntent()
            val pendingIntent = PendingIntent.getActivity(
                context,
                MDM_NOTIFICATION_ID,
                lockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, MDM_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Kích hoạt on mdm")
                .setContentText("Đang gửi tín hiệu on mdm...")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .setOngoing(false)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(MDM_NOTIFICATION_ID, notification)
            true
        } catch (e: Exception) {
            Log.e(TAG, "[LAYER 1] ❌ Lỗi Full-Screen Notification: ${e.message}", e)
            false
        }
    }

    /**
     * Hủy thông báo kích hoạt MDM khỏi thanh trạng thái.
     */
    fun cancelMdmNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(MDM_NOTIFICATION_ID)
            Log.d(TAG, "Đã xóa thông báo MDM Lock")
        } catch (e: Exception) {
            Log.e(TAG, "Không thể xóa thông báo: ${e.message}")
        }
    }

    /**
     * Tạo kênh riêng cho MDM Lock nếu chưa có.
     * Dùng IMPORTANCE_HIGH (không phải DEFAULT) để One UI ưu tiên hiển thị.
     */
    private fun ensureMdmChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(MDM_CHANNEL_ID) == null) {
                val channel = NotificationChannel(MDM_CHANNEL_ID, MDM_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Kênh kích hoạt on mdm tự động"
                    setSound(null, null)           // Không phát âm thanh (tránh làm phiền)
                    enableVibration(false)          // Không rung
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
                Log.d(TAG, "Đã tạo kênh MDM Lock: $MDM_CHANNEL_ID")
            }
        }
    }

    /**
     * Layer 2 & 3: Thử startActivity trực tiếp.
     * WakeLock phải được acquire trước khi gọi hàm này.
     */
    private fun tryStartActivity(context: Context, tag: String): Boolean {
        return try {
            context.startActivity(buildLockIntent())
            Log.d(TAG, "[$tag] ✅ startActivity thành công")
            true
        } catch (e: Exception) {
            Log.e(TAG, "[$tag] ❌ startActivity thất bại: ${e.message}")
            false
        }
    }

    /**
     * Đánh thức màn hình ngay nếu đang tắt.
     * SCREEN_BRIGHT_WAKE_LOCK (deprecated nhưng vẫn hoạt động trên One UI) kết hợp
     * ACQUIRE_CAUSES_WAKEUP để bật màn hình ngay, không cần nhấn nút.
     * Tự release sau 6 giây để tránh lãng phí pin.
     */
    private fun wakeScreenIfNeeded(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            Log.d(TAG, "Màn hình đang tắt — đánh thức WakeLock")
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        or PowerManager.ACQUIRE_CAUSES_WAKEUP
                        or PowerManager.ON_AFTER_RELEASE,
                "LichNoti:MDMLockWake"
            )
            wl.acquire(6_000L)
        } else {
            Log.d(TAG, "Màn hình đang bật — không cần WakeLock")
        }
    }

    private fun markSent() {
        lastLockSentMs = System.currentTimeMillis()
    }

    /**
     * Xây dựng Intent gửi đến VSelfLock MainActivity với action "lock".
     *
     * Flags được chọn kỹ cho One UI:
     * - NEW_TASK: bắt buộc khi gọi từ non-Activity context
     * - SINGLE_TOP: nếu VSelfLock đang ở top thì gọi onNewIntent, KHÔNG destroy rồi tạo mới
     *   (CLEAR_TOP trước đây gây hiện tượng "mở rồi tắt ngay" do destroy instance đang chạy)
     * - NO_ANIMATION + EXCLUDE_FROM_RECENT: ẩn khỏi Recent Apps
     */
    private fun buildLockIntent(): Intent {
        return Intent().apply {
            component = ComponentName(VSELFLOCK_PACKAGE, VSELFLOCK_ACTIVITY)
            action = "lock"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
    }
}
