package com.sdv.lichnoti

enum class AlarmStopReason {
    MANUAL_STOP,
    MANUAL_SNOOZE,
    AUTOMATIC_MDM,
    AUTOMATIC_TIMEOUT,
    TASK_REMOVED
}

object AlarmStopPolicy {
    fun shouldStopRinging(reason: AlarmStopReason): Boolean {
        return reason == AlarmStopReason.MANUAL_STOP ||
            reason == AlarmStopReason.MANUAL_SNOOZE
    }
}
