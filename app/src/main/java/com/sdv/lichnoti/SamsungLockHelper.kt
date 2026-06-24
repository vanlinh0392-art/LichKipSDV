package com.sdv.lichnoti

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

object SamsungLockHelper {
    private const val TAG = "SamsungLockHelper"

    fun sendLockIntent(context: Context) {
        try {
            val intent = Intent().apply {
                component = ComponentName("com.samsung.s1.vselflock", "com.samsung.s1.vselflock.ui.MainActivity")
                action = "lock"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Đã gửi thành công Intent lock tới Samsung VSelfLock")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi gửi Intent lock tới Samsung VSelfLock (Có thể app chưa được cài đặt)", e)
        }
    }
}
