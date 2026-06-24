package com.sdv.lichnoti

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Màn hình cài đặt: chọn kíp, giờ thông báo, app đích, chế độ tối.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    // Views
    private lateinit var rgCrew: RadioGroup
    private lateinit var tvDayTime: TextView
    private lateinit var tvNightTime: TextView
    private lateinit var rgAction: RadioGroup
    private lateinit var rbOpenSelf: RadioButton
    private lateinit var rbOpenOther: RadioButton
    private lateinit var tilPackage: TextInputLayout
    private lateinit var etPackage: TextInputEditText
    private lateinit var switchDarkMode: SwitchMaterial

    // Lưu tạm giờ thông báo khi user chọn
    private var dayHour = 6
    private var dayMinute = 30
    private var nightHour = 18
    private var nightMinute = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = AppPreferences(this)
        bindViews()
        loadCurrentSettings()
        setupListeners()
    }

    private fun bindViews() {
        rgCrew = findViewById(R.id.rgCrew)
        tvDayTime = findViewById(R.id.tvDayTime)
        tvNightTime = findViewById(R.id.tvNightTime)
        rgAction = findViewById(R.id.rgAction)
        rbOpenSelf = findViewById(R.id.rbOpenSelf)
        rbOpenOther = findViewById(R.id.rbOpenOther)
        tilPackage = findViewById(R.id.tilPackage)
        etPackage = findViewById(R.id.etPackage)
        switchDarkMode = findViewById(R.id.switchDarkMode)
    }

    // ── Load cài đặt hiện tại ───────────────────────────────────────────
    private fun loadCurrentSettings() {
        // Kíp
        when (prefs.selectedCrew) {
            "A" -> findViewById<RadioButton>(R.id.rbCrewA).isChecked = true
            "B" -> findViewById<RadioButton>(R.id.rbCrewB).isChecked = true
            "C" -> findViewById<RadioButton>(R.id.rbCrewC).isChecked = true
            "HC" -> findViewById<RadioButton>(R.id.rbCrewHC).isChecked = true
        }

        // Giờ thông báo
        dayHour = prefs.dayNotificationHour
        dayMinute = prefs.dayNotificationMinute
        nightHour = prefs.nightNotificationHour
        nightMinute = prefs.nightNotificationMinute
        updateTimeDisplay()

        // Hành động khi bấm thông báo
        if (prefs.openOtherApp) {
            rbOpenOther.isChecked = true
            tilPackage.visibility = View.VISIBLE
        } else {
            rbOpenSelf.isChecked = true
            tilPackage.visibility = View.GONE
        }
        etPackage.setText(prefs.targetPackage)

        // Chế độ tối
        switchDarkMode.isChecked = prefs.darkMode == "dark"
    }

    private fun updateTimeDisplay() {
        tvDayTime.text = String.format("%02d:%02d", dayHour, dayMinute)
        tvNightTime.text = String.format("%02d:%02d", nightHour, nightMinute)
    }

    // ── Thiết lập sự kiện ───────────────────────────────────────────────
    private fun setupListeners() {
        // Nút quay lại
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // Chọn giờ ca Ngày (giới hạn 6:00-7:50)
        tvDayTime.setOnClickListener {
            showTimePicker(dayHour, dayMinute, "Giờ thông báo ca Ngày") { h, m ->
                dayHour = h
                dayMinute = m
                updateTimeDisplay()
            }
        }
        // Bấm vào cả layout cha cũng mở picker
        findViewById<View>(R.id.layoutDayTime).setOnClickListener {
            tvDayTime.performClick()
        }

        // Chọn giờ ca Đêm (giới hạn 18:00-19:50)
        tvNightTime.setOnClickListener {
            showTimePicker(nightHour, nightMinute, "Giờ thông báo ca Đêm") { h, m ->
                nightHour = h
                nightMinute = m
                updateTimeDisplay()
            }
        }
        findViewById<View>(R.id.layoutNightTime).setOnClickListener {
            tvNightTime.performClick()
        }

        // Toggle hiển thị package khi chọn "Mở app khác"
        rgAction.setOnCheckedChangeListener { _, checkedId ->
            tilPackage.visibility = if (checkedId == R.id.rbOpenOther) View.VISIBLE else View.GONE
        }

        // Nút lưu
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { saveSettings() }
    }

    // ── TimePicker dialog ───────────────────────────────────────────────
    private fun showTimePicker(
        currentHour: Int,
        currentMinute: Int,
        title: String,
        onTimeSet: (Int, Int) -> Unit
    ) {
        val dialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute -> onTimeSet(hourOfDay, minute) },
            currentHour,
            currentMinute,
            true // 24h format
        )
        dialog.setTitle(title)
        dialog.show()
    }

    // ── Lưu tất cả cài đặt ─────────────────────────────────────────────
    private fun saveSettings() {
        // Kíp
        val crewId = when (rgCrew.checkedRadioButtonId) {
            R.id.rbCrewA -> "A"
            R.id.rbCrewB -> "B"
            R.id.rbCrewC -> "C"
            R.id.rbCrewHC -> "HC"
            else -> "A"
        }
        prefs.selectedCrew = crewId

        // Giờ thông báo
        prefs.dayNotificationHour = dayHour
        prefs.dayNotificationMinute = dayMinute
        prefs.nightNotificationHour = nightHour
        prefs.nightNotificationMinute = nightMinute

        // Hành động bấm thông báo
        prefs.openOtherApp = rbOpenOther.isChecked
        prefs.targetPackage = etPackage.text?.toString()?.trim() ?: ""


        // Chế độ tối
        val newDarkMode = if (switchDarkMode.isChecked) "dark" else "light"
        val oldDarkMode = prefs.darkMode
        prefs.darkMode = newDarkMode

        // Lên lịch lại thông báo
        if (prefs.notificationEnabled) {
            NotificationScheduler.scheduleNext(this)
        }

        Toast.makeText(this, "✅ Đã lưu cài đặt!", Toast.LENGTH_SHORT).show()

        // Đổi theme nếu khác
        if (oldDarkMode != newDarkMode) {
            when (newDarkMode) {
                "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        finish()
    }
}
