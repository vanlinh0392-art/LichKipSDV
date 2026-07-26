package com.sdv.lichnoti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MdmPendingPolicyTest {

    @Test
    fun createState_expiresAfterFourHours() {
        val createdAt = 1_000L

        val state = MdmPendingPolicy.createState("alarm-1", createdAt, "alarm")

        assertEquals("alarm-1", state.eventId)
        assertEquals(createdAt + 4 * 60 * 60 * 1000L, state.expiresAtMs)
        assertEquals(0, state.attemptCount)
        assertEquals(MdmAttemptResult.PENDING, state.lastResult)
    }

    @Test
    fun nextRetryAt_usesConfiguredOffsetsAndStopsAtExpiry() {
        val state = MdmPendingPolicy.createState("alarm-1", 10_000L, "alarm")

        assertEquals(10_000L + 2 * 60 * 1000L, MdmPendingPolicy.nextRetryAt(state, 10_000L))
        assertEquals(10_000L + 5 * 60 * 1000L, MdmPendingPolicy.nextRetryAt(state, 10_000L + 2 * 60 * 1000L))
        assertEquals(10_000L + 240 * 60 * 1000L, MdmPendingPolicy.nextRetryAt(state, 10_000L + 120 * 60 * 1000L))
        assertNull(MdmPendingPolicy.nextRetryAt(state, state.expiresAtMs))
    }

    @Test
    fun recordAttempt_incrementsCountAndStoresResult() {
        val state = MdmPendingPolicy.createState("alarm-1", 1_000L, "alarm")

        val updated = MdmPendingPolicy.recordAttempt(
            state,
            attemptedAtMs = 5_000L,
            trigger = "user_present",
            result = MdmAttemptResult.DEFERRED_LOCKED
        )

        assertEquals(1, updated.attemptCount)
        assertEquals(5_000L, updated.lastAttemptAtMs)
        assertEquals("user_present", updated.lastTrigger)
        assertEquals(MdmAttemptResult.DEFERRED_LOCKED, updated.lastResult)
    }

    @Test
    fun debounce_appliesOnlyToSameEventInsideWindow() {
        val attempted = MdmPendingPolicy.recordAttempt(
            MdmPendingPolicy.createState("alarm-1", 1_000L, "alarm"),
            attemptedAtMs = 10_000L,
            trigger = "retry",
            result = MdmAttemptResult.LAUNCH_FAILED
        )

        assertTrue(MdmPendingPolicy.shouldDebounce(attempted, "alarm-1", 15_000L))
        assertFalse(MdmPendingPolicy.shouldDebounce(attempted, "alarm-2", 15_000L))
        assertFalse(MdmPendingPolicy.shouldDebounce(attempted, "alarm-1", 20_000L))
    }

    @Test
    fun expiry_isInclusive() {
        val state = MdmPendingPolicy.createState("alarm-1", 1_000L, "alarm")

        assertFalse(MdmPendingPolicy.isExpired(state, state.expiresAtMs - 1))
        assertTrue(MdmPendingPolicy.isExpired(state, state.expiresAtMs))
    }

    @Test
    fun recover_returnsLiveStateAndDropsExpiredState() {
        val state = MdmPendingPolicy.createState("alarm-1", 1_000L, "alarm")

        assertEquals(state, MdmPendingPolicy.recover(state, state.expiresAtMs - 1))
        assertNull(MdmPendingPolicy.recover(state, state.expiresAtMs))
    }

    @Test
    fun manualSnoozeCancelsButAutoSnoozeRetainsSameEvent() {
        val state = MdmPendingPolicy.createState("alarm-1", 1_000L, "alarm")

        assertNull(MdmPendingPolicy.afterSnooze(state, manual = true))
        assertEquals(state, MdmPendingPolicy.afterSnooze(state, manual = false))
    }

    @Test
    fun newAlarmReplacesOldStateWithoutExtendingOldEvent() {
        val oldState = MdmPendingPolicy.createState("alarm-1", 1_000L, "alarm")
        val newState = MdmPendingPolicy.createState("alarm-2", 5_000L, "alarm")

        assertFalse(oldState.eventId == newState.eventId)
        assertEquals(5_000L, newState.createdAtMs)
        assertEquals(5_000L + MdmPendingPolicy.TTL_MS, newState.expiresAtMs)
    }
}
