package com.sdv.lichnoti

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object MdmRetryScheduler {
    private const val TAG = "MdmRetryScheduler"
    private const val REQUEST_CODE = 2101

    fun scheduleNext(context: Context, state: PendingMdmState, nowMs: Long = System.currentTimeMillis()) {
        val triggerAt = MdmPendingPolicy.nextRetryAt(state, nowMs) ?: return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = retryPendingIntent(context, state.eventId)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            Log.d(TAG, "Đã hẹn retry MDM lúc $triggerAt cho ${state.eventId}")
        } catch (e: SecurityException) {
            Log.w(TAG, "Không có quyền exact alarm, dùng inexact retry", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(retryPendingIntent(context, ""))
    }

    private fun retryPendingIntent(context: Context, eventId: String): PendingIntent {
        val intent = Intent(context, MdmRetryReceiver::class.java).apply {
            action = MdmRetryReceiver.ACTION_RETRY_MDM
            putExtra(MdmPendingCoordinator.EXTRA_EVENT_ID, eventId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
