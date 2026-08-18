package com.sdv.lichnoti

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast

/**
 * Helper điều hướng Intent mở trang Cài đặt Tự khởi chạy (Auto-start)
 * và Tối ưu hóa Pin của các hãng máy (Samsung, Xiaomi, OPPO, Vivo, Huawei).
 */
object OemPermissionHelper {

    private const val TAG = "OemPermissionHelper"

    fun isSamsung(): Boolean =
        Build.MANUFACTURER.contains("samsung", ignoreCase = true)

    fun isXiaomi(): Boolean {
        val m = Build.MANUFACTURER
        return m.contains("xiaomi", ignoreCase = true) ||
                m.contains("redmi", ignoreCase = true) ||
                m.contains("poco", ignoreCase = true)
    }

    fun isOppo(): Boolean {
        val m = Build.MANUFACTURER
        return m.contains("oppo", ignoreCase = true) ||
                m.contains("realme", ignoreCase = true) ||
                m.contains("oneplus", ignoreCase = true)
    }

    fun isVivo(): Boolean {
        val m = Build.MANUFACTURER
        return m.contains("vivo", ignoreCase = true) ||
                m.contains("iqoo", ignoreCase = true)
    }

    fun isHuawei(): Boolean {
        val m = Build.MANUFACTURER
        return m.contains("huawei", ignoreCase = true) ||
                m.contains("honor", ignoreCase = true)
    }

    fun getOemName(): String {
        return when {
            isSamsung() -> "Samsung (One UI)"
            isXiaomi() -> "Xiaomi (MIUI/HyperOS)"
            isOppo() -> "OPPO / Realme (ColorOS)"
            isVivo() -> "Vivo / iQOO (FuntouchOS)"
            isHuawei() -> "Huawei / Honor (HarmonyOS)"
            else -> Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Mở màn hình quản lý Tự khởi chạy (Auto-start / Auto-launch) theo từng hãng máy
     */
    fun openAutoStartSettings(context: Context): Boolean {
        val packageName = context.packageName

        val intents = when {
            isXiaomi() -> listOf(
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
            )

            isOppo() -> listOf(
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelga设置.PowerConsumptionActivity"))
            )

            isVivo() -> listOf(
                Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.MainGuideActivity"))
            )

            isHuawei() -> listOf(
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
            )

            else -> emptyList()
        }

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Thử intent OEM thất bại: ${e.message}")
            }
        }

        // Fallback mở App Details Settings
        return openAppDetailsSettings(context)
    }

    /**
     * Mở cài đặt chi tiết của ứng dụng
     */
    fun openAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Không thể mở Application Details Settings", e)
            Toast.makeText(context, "Không thể mở Cài đặt ứng dụng", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Mở màn hình cấp quyền Báo thức chính xác (Android 12+)
     */
    fun openExactAlarmSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            openAppDetailsSettings(context)
        }
    }

    /**
     * Mở màn hình cấp quyền Thông báo toàn màn hình (Android 14+)
     */
    fun openFullScreenIntentSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            openAppDetailsSettings(context)
        }
    }
}
