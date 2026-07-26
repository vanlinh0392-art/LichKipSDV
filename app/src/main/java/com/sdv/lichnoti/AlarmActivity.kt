package com.sdv.lichnoti

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AlarmActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var eventId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenWindow()

        val prefs = AppPreferences(this)
        eventId = intent.getStringExtra(AlarmService.EXTRA_MDM_EVENT_ID)
            ?: MdmPendingCoordinator.currentState(this)?.eventId

        if (intent.getBooleanExtra("EXTRA_AUTO_STOP_AND_LOCK", false)) {
            handleStopAndLock("notification_stop")
            return
        }

        setContentView(R.layout.activity_alarm)
        bindAlarmContent(prefs)
        bindActions(prefs)
        if (prefs.autoLockSamsung &&
            MdmDeviceState.isUnlockedAndInteractive(this)
        ) {
            window.decorView.post { attemptMdmWithoutStopping("alarm_activity_unlocked") }
        }
    }

    private fun configureLockScreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
    }

    private fun bindAlarmContent(prefs: AppPreferences) {
        val crewId = intent.getStringExtra(AlarmService.EXTRA_CREW_ID) ?: "A"
        val shiftLabel = intent.getStringExtra(AlarmService.EXTRA_SHIFT_LABEL) ?: "Ngày"
        val shiftEmoji = intent.getStringExtra(AlarmService.EXTRA_SHIFT_EMOJI) ?: "☀️"
        val crewName = ShiftCalculator.CREWS.find { it.id == crewId }?.name ?: "Kíp $crewId"

        findViewById<TextView>(R.id.tvAlarmCrew).text = crewName
        findViewById<TextView>(R.id.tvAlarmShift).text = "$shiftEmoji Ca $shiftLabel"
        findViewById<TextView>(R.id.tvAlarmMessage).text = prefs.notificationContent

        val snoozeButton = findViewById<Button>(R.id.btnSnoozeAlarm)
        if (prefs.snoozeDuration == -1) {
            snoozeButton.visibility = View.GONE
        } else {
            snoozeButton.visibility = View.VISIBLE
            snoozeButton.text = "NHẮC LẠI SAU ${prefs.snoozeDuration} PHÚT"
        }

        val colorHex = if (shiftLabel.contains("Ngày")) prefs.dayColor else prefs.nightColor
        runCatching {
            findViewById<View>(R.id.layoutAlarmRoot).background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor(colorHex), Color.parseColor("#090D16"))
            )
        }
    }

    private fun bindActions(prefs: AppPreferences) {
        findViewById<Button>(R.id.btnStopAlarm).setOnClickListener {
            handleStopAndLock("manual_stop")
        }
        findViewById<Button>(R.id.btnSnoozeAlarm).setOnClickListener {
            MdmPendingCoordinator.cancelForManualSnooze(this, eventId)
            sendAlarmAction(AlarmReceiver.ACTION_SNOOZE, manualSnooze = true)
            finish()
        }
    }

    private fun handleStopAndLock(trigger: String) {
        if (AlarmStopPolicy.shouldStopRinging(AlarmStopReason.MANUAL_STOP)) {
            stopService(Intent(this, AlarmService::class.java))
        }
        sendAlarmAction(AlarmReceiver.ACTION_STOP, manualSnooze = false)

        if (AppPreferences(this).autoLockSamsung && eventId != null) {
            val result = MdmPendingCoordinator.attempt(
                context = this,
                trigger = trigger,
                foregroundActivity = this,
                force = true
            )
            Log.d("AlarmActivity", "MDM attempt $trigger -> $result")
            if (MdmPendingCoordinator.currentState(this) != null) {
                MdmPendingService.start(this)
            }
        }
        finish()
    }

    private fun attemptMdmWithoutStopping(trigger: String) {
        if (eventId == null) return
        val result = MdmPendingCoordinator.attempt(
            context = this,
            trigger = trigger,
            foregroundActivity = this,
            force = true
        )
        Log.d("AlarmActivity", "MDM attempt without stopping alarm $trigger -> $result")
        if (MdmPendingCoordinator.currentState(this) != null) {
            MdmPendingService.start(this)
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = AppPreferences(this)
        if (eventId != null &&
            prefs.autoLockSamsung &&
            MdmDeviceState.isUnlockedAndInteractive(this)
        ) {
            window.decorView.post { attemptMdmWithoutStopping("alarm_activity_resume") }
        }
    }

    private fun sendAlarmAction(action: String, manualSnooze: Boolean) {
        sendBroadcast(Intent(this, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra(AlarmReceiver.EXTRA_MANUAL_SNOOZE, manualSnooze)
            eventId?.let { putExtra(MdmPendingCoordinator.EXTRA_EVENT_ID, it) }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        if (!AlarmService.isRunning || isFinishing || SamsungLockHelper.isLockJustSent()) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        var phoneCallActive = false
        runCatching {
            val telephony = getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            phoneCallActive = telephony.callState != android.telephony.TelephonyManager.CALL_STATE_IDLE
        }
        if (powerManager.isInteractive && !phoneCallActive) {
            handler.postDelayed({
                if (AlarmService.isRunning && !isFinishing) {
                    startActivity(Intent(this, AlarmActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        eventId?.let { putExtra(AlarmService.EXTRA_MDM_EVENT_ID, it) }
                    })
                }
            }, 500L)
        }
    }

    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("MissingSuperCall")
    override fun onBackPressed() = Unit

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }
}
