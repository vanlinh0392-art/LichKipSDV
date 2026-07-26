package com.sdv.lichnoti

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class MdmDispatchActivity : AppCompatActivity() {
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (handled) return
        handled = true

        val expectedId = intent.getStringExtra(MdmPendingCoordinator.EXTRA_EVENT_ID)
        val state = MdmPendingCoordinator.currentState(this)
        if (state == null || (!expectedId.isNullOrBlank() && expectedId != state.eventId)) {
            finishWithoutAnimation()
            return
        }

        val trigger = intent.getStringExtra(MdmPendingCoordinator.EXTRA_TRIGGER) ?: "dispatch_activity"
        val result = MdmPendingCoordinator.attempt(
            context = this,
            trigger = trigger,
            foregroundActivity = this,
            force = true
        )
        if (result != MdmAttemptResult.DISPATCHED && MdmPendingCoordinator.currentState(this) != null) {
            MdmPendingService.start(this)
        }
        finishWithoutAnimation()
    }

    private fun finishWithoutAnimation() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
