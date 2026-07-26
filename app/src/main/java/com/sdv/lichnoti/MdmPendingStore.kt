package com.sdv.lichnoti

import android.content.Context
import android.util.Log

object MdmPendingStore {
    private const val TAG = "MdmPendingStore"
    private const val PREFS_NAME = "mdm_pending_state"
    private const val KEY_EVENT_ID = "event_id"
    private const val KEY_CREATED_AT = "created_at"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_ORIGIN = "origin"
    private const val KEY_ATTEMPT_COUNT = "attempt_count"
    private const val KEY_LAST_ATTEMPT_AT = "last_attempt_at"
    private const val KEY_LAST_TRIGGER = "last_trigger"
    private const val KEY_LAST_RESULT = "last_result"

    @Synchronized
    fun load(context: Context): PendingMdmState? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val eventId = prefs.getString(KEY_EVENT_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val createdAt = prefs.getLong(KEY_CREATED_AT, 0L)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (createdAt <= 0L || expiresAt <= createdAt) {
            clear(context)
            return null
        }

        val result = runCatching {
            MdmAttemptResult.valueOf(
                prefs.getString(KEY_LAST_RESULT, MdmAttemptResult.PENDING.name)
                    ?: MdmAttemptResult.PENDING.name
            )
        }.getOrDefault(MdmAttemptResult.PENDING)

        return PendingMdmState(
            eventId = eventId,
            createdAtMs = createdAt,
            expiresAtMs = expiresAt,
            origin = prefs.getString(KEY_ORIGIN, "alarm") ?: "alarm",
            attemptCount = prefs.getInt(KEY_ATTEMPT_COUNT, 0),
            lastAttemptAtMs = prefs.getLong(KEY_LAST_ATTEMPT_AT, 0L),
            lastTrigger = prefs.getString(KEY_LAST_TRIGGER, "") ?: "",
            lastResult = result
        )
    }

    @Synchronized
    fun save(context: Context, state: PendingMdmState): Boolean {
        val committed = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EVENT_ID, state.eventId)
            .putLong(KEY_CREATED_AT, state.createdAtMs)
            .putLong(KEY_EXPIRES_AT, state.expiresAtMs)
            .putString(KEY_ORIGIN, state.origin)
            .putInt(KEY_ATTEMPT_COUNT, state.attemptCount)
            .putLong(KEY_LAST_ATTEMPT_AT, state.lastAttemptAtMs)
            .putString(KEY_LAST_TRIGGER, state.lastTrigger)
            .putString(KEY_LAST_RESULT, state.lastResult.name)
            .commit()
        if (!committed) Log.e(TAG, "Không thể ghi trạng thái MDM pending")
        return committed
    }

    @Synchronized
    fun clear(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
