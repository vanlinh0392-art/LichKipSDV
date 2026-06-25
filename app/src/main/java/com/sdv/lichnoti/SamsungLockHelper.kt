package com.sdv.lichnoti

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helper tối ưu cho Samsung One UI: gửi Intent mở VSelfLock để kích hoạt khóa MDM.
 *
 * Nguyên tắc thiết kế:
 * 1. Chỉ gửi intent đúng 1 lần mỗi lần alarm fire (tránh duplicate).
 * 2. Khi gọi từ foreground (AlarmActivity) → gửi ngay, chắc chắn thành công.
 * 3. Khi gọi từ background (AlarmReceiver snoozeDuration=0) → delay + retry.
 * 4. Log chi tiết để debug trên thiết bị thực.
 */
object SamsungLockHelper {
    private const val TAG = "SamsungLockHelper"
    private const val VSELFLOCK_PACKAGE = "com.samsung.s1.vselflock"
    private const val VSELFLOCK_ACTIVITY = "com.samsung.s1.vselflock.ui.MainActivity"

    // Delay trước khi gửi từ background (ms) — chờ WakeLock đánh thức màn hình
    private const val BACKGROUND_DELAY_MS = 1500L
    // Delay retry lần 2 nếu lần 1 thất bại (ms)
    private const val RETRY_DELAY_MS = 2000L

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Kiểm tra app VSelfLock đã được cài đặt trên thiết bị chưa.
     */
    fun isVSelfLockInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(VSELFLOCK_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Gửi intent mở VSelfLock ngay lập tức.
     * Dùng khi gọi từ foreground context (AlarmActivity đang hiển thị).
     *
     * @param context Context gọi (nên là Activity context)
     * @return true nếu gửi thành công, false nếu thất bại
     */
    fun sendLockIntent(context: Context): Boolean {
        val callerName = context.javaClass.simpleName
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isInstalled = isVSelfLockInstalled(context)
        val hasOverlay = Settings.canDrawOverlays(context)

        Log.d(TAG, """
            |=== MDM Lock Intent ===
            |caller: $callerName
            |isInteractive: ${pm.isInteractive}
            |canDrawOverlays: $hasOverlay
            |isVSelfLockInstalled: $isInstalled
            |mode: IMMEDIATE (foreground)
            |timestamp: ${System.currentTimeMillis()}
        """.trimMargin())

        if (!isInstalled) {
            Log.w(TAG, "VSelfLock chưa được cài đặt — bỏ qua gửi intent")
            return false
        }

        return try {
            val intent = buildLockIntent()
            context.startActivity(intent)
            Log.d(TAG, "Đã gửi startActivity thành công tới VSelfLock")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi gửi intent tới VSelfLock: ${e.message}", e)
            false
        }
    }

    /**
     * Gửi intent mở VSelfLock với delay và retry.
     * Dùng khi gọi từ background context (AlarmReceiver, Service) nơi
     * mà One UI có thể chặn background activity start.
     *
     * Flow:
     * 1. Đánh thức màn hình bằng WakeLock
     * 2. Chờ [BACKGROUND_DELAY_MS] ms để màn hình kịp bật
     * 3. Gửi intent lần 1
     * 4. Nếu lần 1 thất bại → chờ [RETRY_DELAY_MS] ms → gửi lần 2
     *
     * @param context Context gọi (Service hoặc BroadcastReceiver context)
     */
    fun sendLockIntentWithDelay(context: Context) {
        val callerName = context.javaClass.simpleName
        val appContext = context.applicationContext

        Log.d(TAG, """
            |=== MDM Lock Intent (Background) ===
            |caller: $callerName
            |canDrawOverlays: ${Settings.canDrawOverlays(appContext)}
            |isVSelfLockInstalled: ${isVSelfLockInstalled(appContext)}
            |mode: DELAYED ($BACKGROUND_DELAY_MS ms) + RETRY
            |timestamp: ${System.currentTimeMillis()}
        """.trimMargin())

        if (!isVSelfLockInstalled(appContext)) {
            Log.w(TAG, "VSelfLock chưa được cài đặt — bỏ qua gửi intent")
            return
        }

        // Bước 1: Đánh thức màn hình
        wakeScreenIfNeeded(appContext)

        // Bước 2: Delay rồi gửi intent
        handler.postDelayed({
            val success = trySendLockIntent(appContext, "attempt_1")
            if (!success) {
                // Bước 3: Retry lần 2
                Log.d(TAG, "Lần 1 thất bại — retry sau ${RETRY_DELAY_MS}ms")
                handler.postDelayed({
                    trySendLockIntent(appContext, "attempt_2_retry")
                }, RETRY_DELAY_MS)
            }
        }, BACKGROUND_DELAY_MS)
    }

    /**
     * Đánh thức màn hình nếu đang tắt.
     * Cần thiết trên Samsung One UI vì hệ thống chặn background activity start
     * khi màn hình tắt, ngay cả khi app có quyền overlay.
     */
    private fun wakeScreenIfNeeded(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            Log.d(TAG, "Màn hình đang tắt — đánh thức bằng WakeLock")
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        or PowerManager.ACQUIRE_CAUSES_WAKEUP
                        or PowerManager.ON_AFTER_RELEASE,
                "LichNoti:MDMLockWake"
            )
            wl.acquire(5_000L) // Giữ sáng 5 giây
        } else {
            Log.d(TAG, "Màn hình đang bật — không cần WakeLock")
        }
    }

    /**
     * Thử gửi intent 1 lần. Trả về true nếu startActivity không throw exception.
     */
    private fun trySendLockIntent(context: Context, attemptTag: String): Boolean {
        return try {
            val intent = buildLockIntent()
            context.startActivity(intent)
            Log.d(TAG, "[$attemptTag] Đã gửi startActivity thành công tới VSelfLock")
            true
        } catch (e: Exception) {
            Log.e(TAG, "[$attemptTag] Lỗi gửi intent tới VSelfLock: ${e.message}", e)
            false
        }
    }

    /**
     * Tạo Intent tối ưu cho Samsung One UI.
     *
     * Phân tích res/xml/shortcuts.xml của VSelfLock xác nhận:
     * - App Shortcut "lock" (기능 잠금): action="lock", enabled=true
     * - App Shortcut "unlock" (잠금 해제): action="unlock", enabled=false
     * - App Shortcut "uninstall": action="uninstall", enabled=true
     *
     * Action "lock" KHÔNG nằm trong intent-filter của Manifest nhưng ĐƯỢC
     * VSelfLock xử lý qua getIntent().getAction() trong MainActivity.
     * Đây chính là cách VSelfLock phân biệt giữa mở app bình thường và yêu cầu khóa.
     *
     * Flags:
     * - FLAG_ACTIVITY_CLEAR_TOP + SINGLE_TOP: tránh tạo instance mới nếu đã mở.
     * - FLAG_ACTIVITY_NO_ANIMATION: tránh flash animation gây khó chịu.
     * - FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS: không hiện trong Recent apps.
     */
    private fun buildLockIntent(): Intent {
        return Intent().apply {
            component = ComponentName(VSELFLOCK_PACKAGE, VSELFLOCK_ACTIVITY)
            action = "lock"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
    }
}
