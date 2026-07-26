package com.sdv.lichnoti

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log

/** Samsung-only bridge to the VSelfLock activity. Pending/retry ownership lives in
 * [MdmPendingCoordinator]; this helper performs one explicit foreground dispatch only. */
object SamsungLockHelper {
    private const val TAG = "SamsungLockHelper"
    private const val VSELFLOCK_PACKAGE = "com.samsung.s1.vselflock"
    private const val VSELFLOCK_ACTIVITY = "com.samsung.s1.vselflock.ui.MainActivity"
    private const val LEGACY_MDM_NOTIFICATION_ID = 2001

    @Volatile
    private var lastDispatchRequestedAtMs = 0L

    fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    fun isVSelfLockInstalled(context: Context): Boolean {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(VSELFLOCK_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isVSelfLockTargetAvailable(context: Context): Boolean {
        if (!isSamsungDevice() || !isVSelfLockInstalled(context)) return false
        return try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getActivityInfo(
                ComponentName(VSELFLOCK_PACKAGE, VSELFLOCK_ACTIVITY),
                0
            )
            info.enabled && info.exported
        } catch (e: Exception) {
            Log.w(TAG, "Không resolve được VSelfLock MainActivity", e)
            false
        }
    }

    /** Must be called from a visible Activity. A non-throwing start only confirms dispatch. */
    fun sendLockIntent(context: Context): Boolean {
        if (!isVSelfLockTargetAvailable(context)) {
            Log.w(TAG, "VSelfLock target không khả dụng")
            return false
        }
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Thiếu quyền overlay; giữ nguyên preference để người dùng sửa quyền")
            return false
        }

        lastDispatchRequestedAtMs = System.currentTimeMillis()
        return try {
            context.startActivity(buildLockIntent())
            Log.d(TAG, "Đã dispatch action=lock tới VSelfLock từ ${context.javaClass.simpleName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Không thể dispatch action=lock tới VSelfLock", e)
            false
        }
    }

    /** Compatibility entry point for old callers; durable retry is delegated to the coordinator. */
    fun sendLockIntentWithDelay(context: Context) {
        val state = MdmPendingCoordinator.currentState(context)
            ?: MdmPendingCoordinator.begin(context, origin = "legacy")
            ?: return
        MdmPendingCoordinator.attempt(context, "legacy_delay", force = true)
        if (MdmPendingCoordinator.currentState(context)?.eventId == state.eventId) {
            MdmPendingService.start(context)
        }
    }

    fun resetDebounce() {
        lastDispatchRequestedAtMs = 0L
    }

    fun isLockJustSent(): Boolean {
        return System.currentTimeMillis() - lastDispatchRequestedAtMs < 4_000L
    }

    fun cancelMdmNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(LEGACY_MDM_NOTIFICATION_ID)
    }

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
