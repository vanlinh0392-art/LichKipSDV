package com.sdv.lichnoti

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmStopPolicyTest {
    @Test
    fun onlyManualActionsStopRinging() {
        assertTrue(AlarmStopPolicy.shouldStopRinging(AlarmStopReason.MANUAL_STOP))
        assertTrue(AlarmStopPolicy.shouldStopRinging(AlarmStopReason.MANUAL_SNOOZE))
        assertFalse(AlarmStopPolicy.shouldStopRinging(AlarmStopReason.AUTOMATIC_MDM))
        assertFalse(AlarmStopPolicy.shouldStopRinging(AlarmStopReason.AUTOMATIC_TIMEOUT))
        assertFalse(AlarmStopPolicy.shouldStopRinging(AlarmStopReason.TASK_REMOVED))
    }
}
