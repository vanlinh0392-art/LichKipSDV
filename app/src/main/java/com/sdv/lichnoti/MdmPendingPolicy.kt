package com.sdv.lichnoti

enum class MdmAttemptResult {
    PENDING,
    DISPATCHED,
    DEFERRED_LOCKED,
    BLOCKED_PERMISSION,
    TARGET_MISSING,
    LAUNCH_FAILED,
    EXPIRED
}

data class PendingMdmState(
    val eventId: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val origin: String,
    val attemptCount: Int = 0,
    val lastAttemptAtMs: Long = 0L,
    val lastTrigger: String = "",
    val lastResult: MdmAttemptResult = MdmAttemptResult.PENDING
)

object MdmPendingPolicy {
    const val TTL_MS = 4 * 60 * 60 * 1000L
    const val DEBOUNCE_MS = 10_000L

    private val retryOffsetsMs = longArrayOf(
        2 * 60 * 1000L,
        5 * 60 * 1000L,
        15 * 60 * 1000L,
        30 * 60 * 1000L,
        60 * 60 * 1000L,
        120 * 60 * 1000L,
        240 * 60 * 1000L
    )

    fun createState(eventId: String, createdAtMs: Long, origin: String): PendingMdmState {
        return PendingMdmState(
            eventId = eventId,
            createdAtMs = createdAtMs,
            expiresAtMs = createdAtMs + TTL_MS,
            origin = origin
        )
    }

    fun nextRetryAt(state: PendingMdmState, nowMs: Long): Long? {
        if (isExpired(state, nowMs)) return null
        return retryOffsetsMs
            .asSequence()
            .map { state.createdAtMs + it }
            .firstOrNull { it > nowMs && it <= state.expiresAtMs }
    }

    fun recordAttempt(
        state: PendingMdmState,
        attemptedAtMs: Long,
        trigger: String,
        result: MdmAttemptResult
    ): PendingMdmState {
        return state.copy(
            attemptCount = state.attemptCount + 1,
            lastAttemptAtMs = attemptedAtMs,
            lastTrigger = trigger,
            lastResult = result
        )
    }

    fun shouldDebounce(state: PendingMdmState, eventId: String, nowMs: Long): Boolean {
        return state.eventId == eventId &&
            state.lastAttemptAtMs > 0L &&
            nowMs - state.lastAttemptAtMs < DEBOUNCE_MS
    }

    fun isExpired(state: PendingMdmState, nowMs: Long): Boolean {
        return nowMs >= state.expiresAtMs
    }

    fun recover(state: PendingMdmState, nowMs: Long): PendingMdmState? {
        return state.takeUnless { isExpired(it, nowMs) }
    }

    fun afterSnooze(state: PendingMdmState, manual: Boolean): PendingMdmState? {
        return if (manual) null else state
    }
}
