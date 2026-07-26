package com.sdv.lichnoti

import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationSchedulerIdentityTest {

    @Test
    fun snoozeCannotReplaceNextShiftAlarm() {
        assertNotEquals(
            NotificationScheduler.ALARM_REQUEST_CODE,
            NotificationScheduler.SNOOZE_REQUEST_CODE
        )
    }
}
