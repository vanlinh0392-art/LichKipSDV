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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val autoStopAndLock = intent?.getBooleanExtra("EXTRA_AUTO_STOP_AND_LOCK", false) ?: false
        if (autoStopAndLock) {
            // Tắt nhạc chuông báo thức
            val serviceIntent = Intent(this, AlarmService::class.java)
            stopService(serviceIntent)
            
            // Gửi broadcast để NotificationScheduler lập lịch ca tiếp theo
            sendBroadcastToReceiver(AlarmReceiver.ACTION_STOP)
            
            // Mở VSelfLock để khóa thiết bị — gọi từ foreground Activity nên luôn được phép startActivity
            // (không cần kiểm tra overlay permission vì One UI cho phép foreground context)
            val prefs = AppPreferences(this)
            if (prefs.autoLockSamsung) {
                SamsungLockHelper.resetDebounce()
                SamsungLockHelper.sendLockIntent(this)
            }
            finish()
            return
        }

        // Cấu hình hiển thị trên màn hình khóa và đánh thức thiết bị
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        // Yêu cầu giải phóng keyguard tạm thời
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null)
        }

        setContentView(R.layout.activity_alarm)

        val prefs = AppPreferences(this)

        val crewId = intent.getStringExtra(AlarmService.EXTRA_CREW_ID) ?: "A"
        val shiftLabel = intent.getStringExtra(AlarmService.EXTRA_SHIFT_LABEL) ?: "Ngày"
        val shiftEmoji = intent.getStringExtra(AlarmService.EXTRA_SHIFT_EMOJI) ?: "☀️"

        val crewName = ShiftCalculator.CREWS.find { it.id == crewId }?.name ?: "Kíp $crewId"

        // Set text
        findViewById<TextView>(R.id.tvAlarmCrew).text = crewName
        findViewById<TextView>(R.id.tvAlarmShift).text = "$shiftEmoji Ca $shiftLabel"
        findViewById<TextView>(R.id.tvAlarmMessage).text = prefs.notificationContent

        val btnSnooze = findViewById<Button>(R.id.btnSnoozeAlarm)
        btnSnooze.text = "NHẮC LẠI SAU ${prefs.snoozeDuration} PHÚT"

        // Đổi màu nền gradient động theo màu ca trực
        val layoutRoot = findViewById<View>(R.id.layoutAlarmRoot)
        val isDay = shiftLabel.contains("Ngày")
        val colorHex = if (isDay) prefs.dayColor else prefs.nightColor
        try {
            val colorVal = Color.parseColor(colorHex)
            val gd = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(colorVal, Color.parseColor("#090D16"))
            )
            layoutRoot.background = gd
        } catch (e: Exception) {
            // Giữ background xml mặc định
        }

        // Xử lý sự kiện click
        findViewById<Button>(R.id.btnStopAlarm).setOnClickListener {
            sendBroadcastToReceiver(AlarmReceiver.ACTION_STOP)
            
            // Tự động mở app khác trực tiếp từ Activity đang ở foreground để tránh Android block background activity start
            val prefs = AppPreferences(this)
            if (!prefs.openSelf && prefs.targetPackage.isNotBlank()) {
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(prefs.targetPackage)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Samsung MDM Lock — gọi từ foreground Activity (user bấm DỪNG)
            // Không cần check canDrawOverlays() vì AlarmActivity đang ở foreground,
            // One UI luôn cho phép startActivity từ foreground context.
            if (prefs.autoLockSamsung) {
                SamsungLockHelper.resetDebounce()
                SamsungLockHelper.sendLockIntent(this)
            }

            finish()
        }

        btnSnooze.setOnClickListener {
            sendBroadcastToReceiver(AlarmReceiver.ACTION_SNOOZE)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        // Nếu AlarmService vẫn đang chạy (người dùng chưa bấm nút), tức là báo thức đang reo.
        // Nếu màn hình vẫn đang sáng và không có cuộc gọi điện thoại, tự động đưa AlarmActivity trở lại foreground.
        if (AlarmService.isRunning) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val isInteractive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                pm.isInteractive
            } else {
                @Suppress("DEPRECATION")
                pm.isScreenOn
            }

            // Kiểm tra trạng thái cuộc gọi
            var isPhoneCallActive = false
            try {
                val tm = getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
                isPhoneCallActive = tm.callState != android.telephony.TelephonyManager.CALL_STATE_IDLE
            } catch (e: Exception) {
                // Mặc định là không có cuộc gọi nếu không thể kiểm tra
            }

            if (isInteractive && !isPhoneCallActive) {
                Log.d("AlarmActivity", "AlarmActivity bị pause nhưng báo thức vẫn đang chạy -> Đưa lại lên foreground sau 500ms")
                handler.postDelayed({
                    if (AlarmService.isRunning) {
                        val pm2 = getSystemService(Context.POWER_SERVICE) as PowerManager
                        val isInteractive2 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                            pm2.isInteractive
                        } else {
                            @Suppress("DEPRECATION")
                            pm2.isScreenOn
                        }
                        if (isInteractive2) {
                            val reLaunchIntent = Intent(this, AlarmActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            }
                            startActivity(reLaunchIntent)
                        }
                    }
                }, 500)
            }
        }
    }

    private fun sendBroadcastToReceiver(actionStr: String) {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = actionStr
        }
        sendBroadcast(intent)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Vô hiệu hóa nút back vật lý để người dùng buộc phải bấm Stop hoặc Snooze
    }
}
