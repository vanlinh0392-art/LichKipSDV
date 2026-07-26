package com.sdv.lichnoti

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import java.util.UUID

object MdmPendingCoordinator {
    private const val TAG = "MdmPendingCoordinator"
    const val EXTRA_EVENT_ID = "mdm_event_id"
    const val EXTRA_TRIGGER = "mdm_trigger"

    fun begin(
        context: Context,
        requestedEventId: String? = null,
        origin: String = "alarm",
        nowMs: Long = System.currentTimeMillis()
    ): PendingMdmState? {
        val prefs = AppPreferences(context)
        if (!prefs.autoLockSamsung || !SamsungLockHelper.isSamsungDevice()) return null

        val existing = MdmPendingStore.load(context)
        if (existing != null && !MdmPendingPolicy.isExpired(existing, nowMs)) {
            if (requestedEventId != null && requestedEventId == existing.eventId) {
                MdmRetryScheduler.scheduleNext(context, existing, nowMs)
                return existing
            }
        }

        val eventId = requestedEventId?.takeIf { it.isNotBlank() }
            ?: "$nowMs-${UUID.randomUUID()}"
        if (existing != null && existing.eventId != eventId) {
            MdmPendingService.stop(context)
        }
        val state = MdmPendingPolicy.createState(eventId, nowMs, origin)
        MdmPendingStore.save(context, state)
        MdmRetryScheduler.cancel(context)
        MdmRetryScheduler.scheduleNext(context, state, nowMs)
        Log.d(TAG, "Tạo MDM pending $eventId, hết hạn ${state.expiresAtMs}")
        return state
    }

    @Synchronized
    fun attempt(
        context: Context,
        trigger: String,
        foregroundActivity: Activity? = null,
        force: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): MdmAttemptResult {
        val state = MdmPendingStore.load(context) ?: return MdmAttemptResult.EXPIRED
        if (MdmPendingPolicy.isExpired(state, nowMs)) {
            expire(context, "ttl")
            return MdmAttemptResult.EXPIRED
        }
        if (!AppPreferences(context).autoLockSamsung) {
            cancel(context, "feature_disabled")
            return MdmAttemptResult.BLOCKED_PERMISSION
        }
        val unlocked = MdmDeviceState.isUnlockedAndInteractive(context)
        val debounced = MdmPendingPolicy.shouldDebounce(state, state.eventId, nowMs)
        if (debounced && !force) return state.lastResult
        if (debounced && force && !unlocked) {
            val isBridgeFulfillingItsOwnRequest = foregroundActivity != null &&
                state.lastTrigger == trigger
            if (!isBridgeFulfillingItsOwnRequest) return state.lastResult
        }
        if (!SamsungLockHelper.isVSelfLockTargetAvailable(context)) {
            val result = persistAttempt(context, state, nowMs, trigger, MdmAttemptResult.TARGET_MISSING)
            ensurePendingInfrastructure(context)
            return result
        }
        if (!Settings.canDrawOverlays(context)) {
            val result = persistAttempt(context, state, nowMs, trigger, MdmAttemptResult.BLOCKED_PERMISSION)
            ensurePendingInfrastructure(context)
            return result
        }

        val launched = if (foregroundActivity != null) {
            SamsungLockHelper.sendLockIntent(foregroundActivity)
        } else {
            MdmPendingIntents.sendBridge(context, state.eventId, trigger)
        }

        val result = when {
            !launched -> MdmAttemptResult.LAUNCH_FAILED
            foregroundActivity == null && unlocked -> MdmAttemptResult.PENDING
            !unlocked -> MdmAttemptResult.DEFERRED_LOCKED
            else -> MdmAttemptResult.DISPATCHED
        }
        persistAttempt(context, state, nowMs, trigger, result)

        if (result == MdmAttemptResult.DISPATCHED) {
            complete(context, state.eventId)
        } else {
            ensurePendingInfrastructure(context)
        }
        return result
    }

    fun recover(context: Context, trigger: String): MdmAttemptResult? {
        val storedState = MdmPendingStore.load(context) ?: return null
        val state = MdmPendingPolicy.recover(storedState, System.currentTimeMillis())
        if (state == null) {
            expire(context, trigger)
            return MdmAttemptResult.EXPIRED
        }
        MdmRetryScheduler.scheduleNext(context, state)
        val result = attempt(context, trigger, force = true)
        if (MdmPendingStore.load(context) != null) {
            MdmPendingService.start(context)
        }
        return result
    }

    fun currentState(context: Context): PendingMdmState? = MdmPendingStore.load(context)

    fun complete(context: Context, eventId: String) {
        val current = MdmPendingStore.load(context) ?: return
        if (current.eventId != eventId) return
        Log.d(TAG, "Hoàn tất MDM pending $eventId")
        MdmPendingStore.clear(context)
        MdmRetryScheduler.cancel(context)
        MdmPendingService.stop(context)
        SamsungLockHelper.cancelMdmNotification(context)
    }

    fun cancelForManualSnooze(context: Context, eventId: String?) {
        val current = MdmPendingStore.load(context) ?: return
        if (eventId == null || eventId == current.eventId) cancel(context, "manual_snooze")
    }

    fun expire(context: Context, reason: String) {
        val current = MdmPendingStore.load(context)
        if (current != null) Log.d(TAG, "Hết hạn MDM ${current.eventId}: $reason")
        cancel(context, "expired_$reason")
    }

    fun cancel(context: Context, reason: String) {
        Log.d(TAG, "Hủy MDM pending: $reason")
        MdmPendingStore.clear(context)
        MdmRetryScheduler.cancel(context)
        MdmPendingService.stop(context)
        SamsungLockHelper.cancelMdmNotification(context)
    }

    fun stopAlarmAfterDispatch(context: Context, eventId: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_STOP
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        context.sendBroadcast(intent)
    }

    private fun persistAttempt(
        context: Context,
        state: PendingMdmState,
        nowMs: Long,
        trigger: String,
        result: MdmAttemptResult
    ): MdmAttemptResult {
        val updated = MdmPendingPolicy.recordAttempt(state, nowMs, trigger, result)
        MdmPendingStore.save(context, updated)
        MdmRetryScheduler.scheduleNext(context, updated, nowMs)
        Log.d(TAG, "MDM ${state.eventId}: trigger=$trigger result=$result attempt=${updated.attemptCount}")
        return result
    }

    private fun ensurePendingInfrastructure(context: Context) {
        MdmPendingStore.load(context)?.let { MdmRetryScheduler.scheduleNext(context, it) }
        if (context !is MdmPendingService) {
            MdmPendingService.start(context)
        }
    }
}
