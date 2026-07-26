package com.sdv.lichnoti

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/** Bridge to the installed MDM/VSelfLock activity. Pending/retry ownership lives in
 * [MdmPendingCoordinator]; this helper performs one explicit foreground dispatch only. */
object SamsungLockHelper {
    private const val TAG = "SamsungLockHelper"
    private const val VSELFLOCK_PACKAGE = "com.samsung.s1.vselflock"
    private const val VSELFLOCK_ACTIVITY = "com.samsung.s1.vselflock.ui.MainActivity"
    private const val LEGACY_MDM_NOTIFICATION_ID = 2001

    @Volatile
    private var lastDispatchRequestedAtMs = 0L

    fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.contains("samsung", ignoreCase = true) ||
            Build.BRAND.contains("samsung", ignoreCase = true)
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
        // Package/activity thực tế là tín hiệu tin cậy hơn chuỗi hãng máy.
        return resolveMdmComponent(context) != null
    }

    /** Must be called from a visible Activity. A non-throwing start only confirms dispatch. */
    fun sendLockIntent(context: Context): Boolean {
        val target = resolveMdmComponent(context)
        if (target == null) {
            Log.w(TAG, "VSelfLock target không khả dụng")
            return false
        }
        lastDispatchRequestedAtMs = System.currentTimeMillis()
        return try {
            context.startActivity(buildLockIntent(target))
            Log.d(
                TAG,
                "Đã dispatch action=lock tới ${target.flattenToShortString()} từ ${context.javaClass.simpleName}"
            )
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

    /**
     * Prefer the known VSelfLock activity, then fall back to the package launcher activity.
     * Some non-Samsung ROMs can install the same MDM package with a repackaged/renamed launcher.
     */
    private fun resolveMdmComponent(context: Context): ComponentName? {
        if (!isVSelfLockInstalled(context)) return null
        val packageManager = context.packageManager
        val known = ComponentName(VSELFLOCK_PACKAGE, VSELFLOCK_ACTIVITY)
        val launcher = packageManager.getLaunchIntentForPackage(VSELFLOCK_PACKAGE)?.component
        return listOfNotNull(known, launcher)
            .distinct()
            .firstOrNull { component ->
                runCatching {
                    @Suppress("DEPRECATION")
                    val info = packageManager.getActivityInfo(component, 0)
                    info.enabled && info.exported
                }.getOrDefault(false)
            }
    }

    private fun buildLockIntent(target: ComponentName): Intent {
        return Intent().apply {
            component = target
            action = "lock"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
    }
}
