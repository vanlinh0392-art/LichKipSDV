package com.sdv.lichnoti

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON") {

            // Recreate notification channel
            NotificationHelper.createNotificationChannel(context)

            // Reschedule alarm
            NotificationScheduler.scheduleNext(context)
            NotificationScheduler.scheduleLunarAlarm(context)
            // MDM target activities may remain credential-encrypted until first unlock.
            // Alarm scheduling is direct-boot safe; MDM recovery resumes on normal boot/unlock.
            if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
                action != Intent.ACTION_TIME_CHANGED &&
                action != Intent.ACTION_TIMEZONE_CHANGED) {
                MdmPendingCoordinator.recover(context, "boot_completed")
            }
        }
    }
}
