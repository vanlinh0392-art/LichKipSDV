package com.sdv.lichnoti

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MdmRetryReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_RETRY_MDM = "com.sdv.lichnoti.ACTION_RETRY_MDM"
        private const val TAG = "MdmRetryReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RETRY_MDM) return
        val expectedId = intent.getStringExtra(MdmPendingCoordinator.EXTRA_EVENT_ID)
        val current = MdmPendingCoordinator.currentState(context) ?: return
        if (!expectedId.isNullOrBlank() && expectedId != current.eventId) {
            Log.d(TAG, "Bỏ retry cũ $expectedId, pending hiện tại=${current.eventId}")
            return
        }
        MdmPendingCoordinator.recover(context, "retry_alarm")
    }
}
