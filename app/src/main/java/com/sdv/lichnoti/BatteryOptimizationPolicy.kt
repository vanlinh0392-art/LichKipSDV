package com.sdv.lichnoti

/** Pure UI policy so OEM-specific screens never override Android's real battery exemption. */
object BatteryOptimizationPolicy {
    fun shouldShowWarning(isBatteryUnrestricted: Boolean): Boolean =
        !isBatteryUnrestricted

    fun shouldShowSamsungNeverSleepingShortcut(
        isSamsungDevice: Boolean,
        isBatteryUnrestricted: Boolean
    ): Boolean = isSamsungDevice && !isBatteryUnrestricted
}
