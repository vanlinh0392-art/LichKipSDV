package com.sdv.lichnoti

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryOptimizationPolicyTest {
    @Test
    fun `battery warning is hidden after unrestricted is granted on every manufacturer`() {
        assertFalse(BatteryOptimizationPolicy.shouldShowWarning(isBatteryUnrestricted = true))
    }

    @Test
    fun `battery warning remains visible while unrestricted is missing`() {
        assertTrue(BatteryOptimizationPolicy.shouldShowWarning(isBatteryUnrestricted = false))
    }

    @Test
    fun `never sleeping shortcut is only shown for samsung while battery is restricted`() {
        assertTrue(
            BatteryOptimizationPolicy.shouldShowSamsungNeverSleepingShortcut(
                isSamsungDevice = true,
                isBatteryUnrestricted = false
            )
        )
        assertFalse(
            BatteryOptimizationPolicy.shouldShowSamsungNeverSleepingShortcut(
                isSamsungDevice = true,
                isBatteryUnrestricted = true
            )
        )
        assertFalse(
            BatteryOptimizationPolicy.shouldShowSamsungNeverSleepingShortcut(
                isSamsungDevice = false,
                isBatteryUnrestricted = false
            )
        )
    }
}
