package com.sdv.lichnoti

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

object MdmPendingIntents {
    private const val TAG = "MdmPendingIntents"
    private const val BRIDGE_REQUEST_CODE = 2103

    fun bridgePendingIntent(
        context: Context,
        eventId: String,
        trigger: String
    ): PendingIntent {
        val intent = Intent(context, MdmDispatchActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            putExtra(MdmPendingCoordinator.EXTRA_EVENT_ID, eventId)
            putExtra(MdmPendingCoordinator.EXTRA_TRIGGER, trigger)
        }
        val options = creatorOptions()
        return PendingIntent.getActivity(
            context,
            BRIDGE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            options
        )
    }

    fun sendBridge(context: Context, eventId: String, trigger: String): Boolean {
        return try {
            val pendingIntent = bridgePendingIntent(context, eventId, trigger)
            pendingIntent.send(context, 0, null, null, null, null, senderOptions())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Không thể mở MdmDispatchActivity từ $trigger", e)
            false
        }
    }

    fun activityPendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent
    ): PendingIntent {
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            creatorOptions()
        )
    }

    private fun creatorOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        return ActivityOptions.makeBasic().apply {
            setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        }.toBundle()
    }

    private fun senderOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return ActivityOptions.makeBasic().apply {
            setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        }.toBundle()
    }
}
