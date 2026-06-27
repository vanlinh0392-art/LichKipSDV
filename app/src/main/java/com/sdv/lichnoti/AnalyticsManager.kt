package com.sdv.lichnoti

import android.content.Context
import android.provider.Settings
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Quản lý việc gửi analytics data lên Google Apps Script backend.
 * - Chạy im lặng, không hiển thị gì cho người dùng.
 * - Chỉ gửi 1 lần/ngày.
 * - Fail silently — không retry, không log.
 */
object AnalyticsManager {

    // ═══════════════════════════════════════════════════════════
    // 🔧 THAY URL NÀY BẰNG WEB APP URL TỪ GOOGLE APPS SCRIPT
    // ═══════════════════════════════════════════════════════════
    private const val TRACKING_URL = "https://script.google.com/macros/s/AKfycbwp67rWCpQGev5KecIX286Y2cXV9Kxjci-5rHWZ2ik7HjgCHf0EGEc297Ia6SHNYrCS_g/exec"

    private const val PREF_NAME = "analytics_prefs"
    private const val KEY_LAST_TRACKING_DATE = "last_tracking_date"
    private const val CONNECT_TIMEOUT = 10_000  // 10 giây
    private const val READ_TIMEOUT = 10_000     // 10 giây

    /**
     * Gửi tracking data nếu chưa gửi trong ngày hôm nay.
     * Được gọi từ MainActivity.onCreate().
     * Hoàn toàn im lặng — không Toast, không Dialog, không thông báo.
     */
    fun trackAppLaunch(context: Context) {
        try {
            val today = java.time.LocalDate.now().toString() // yyyy-MM-dd
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val lastDate = prefs.getString(KEY_LAST_TRACKING_DATE, "") ?: ""

            if (lastDate == today) return // Đã gửi hôm nay rồi

            // Lưu ngày ngay lập tức để tránh gửi trùng nếu user mở app liên tục
            prefs.edit().putString(KEY_LAST_TRACKING_DATE, today).apply()

            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: return

            val manufacturer = android.os.Build.MANUFACTURER ?: "Unknown"
            val model = android.os.Build.MODEL ?: "Device"
            val deviceModel = "${manufacturer}_${model}".replace("\\s+".toRegex(), "_")
            val deviceId = "${deviceModel}_$androidId"

            val versionCode: Int
            val versionName: String
            try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    pInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    pInfo.versionCode
                }
                versionName = pInfo.versionName ?: "unknown"
            } catch (_: Exception) {
                return
            }

            // Gửi trong background thread
            Thread {
                sendTrackingData(deviceId, versionCode, versionName)
            }.start()

        } catch (_: Exception) {
            // Fail silently
        }
    }

    /**
     * Gửi HTTP POST đến Google Apps Script Web App.
     * Chạy trong background thread.
     */
    private fun sendTrackingData(deviceId: String, versionCode: Int, versionName: String) {
        try {
            if (TRACKING_URL.startsWith("YOUR_")) return // Chưa cấu hình URL

            val payload = """{"deviceId":"$deviceId","versionCode":$versionCode,"versionName":"$versionName"}"""

            val url = URL(TRACKING_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                doOutput = true
                instanceFollowRedirects = true
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(payload)
                writer.flush()
            }

            // Đọc response để hoàn tất request (GAS cần điều này)
            conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

        } catch (_: Exception) {
            // Fail silently — không ảnh hưởng trải nghiệm người dùng
        }
    }
}
