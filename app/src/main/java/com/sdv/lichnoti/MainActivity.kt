package com.sdv.lichnoti

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.cardview.widget.CardView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.LocalDate
import android.app.TimePickerDialog

/**
 * Màn hình chính điều khiển hiển thị lịch kíp (1 tháng hoặc 2 tuần),
 * cho phép ẩn/hiện lịch, và tích hợp menu cài đặt ở Drawer bên trái.
 * Đã tối ưu hóa luồng báo thức, bỏ qua tối ưu pin để chạy ổn định.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var drawerLayout: DrawerLayout
    
    private var currentYear = 2026
    private var currentMonth = 6

    // Tự động kiểm tra pin khi quay lại app
    private lateinit var cardBatteryWarning: CardView

    // Launcher xin quyền thông báo (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NotificationHelper.createNotificationChannel(this)
            NotificationScheduler.scheduleNext(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = AppPreferences(this)
        applyTheme()
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        cardBatteryWarning = findViewById(R.id.cardBatteryWarning)

        val today = LocalDate.now()
        currentYear = today.year
        currentMonth = today.monthValue

        // Cấu hình Header và Menu Drawer
        setupMenuDrawer()
        setupHeader(today)
        setupCrewBadgeDoubleClick()
        setupMonthNavigation()
        setupCalendarControls()
        setupCalendar()
        setupNextAlarm()
        setupNotificationToggle()
        setupBatteryOptimizationButtons()
        setupDarkModeToggle()

        // Setup click copy STK
        val btnCopyStk = findViewById<View>(R.id.btnCopyStk)
        val copyAction = View.OnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            if (clipboard != null) {
                val clip = android.content.ClipData.newPlainText("STK", "0011004211173")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Đã copy số tài khoản vietcombank!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Không thể truy cập Clipboard hệ thống", Toast.LENGTH_SHORT).show()
            }
        }
        btnCopyStk?.setOnClickListener(copyAction)
        findViewById<View>(R.id.layoutDonation)?.setOnClickListener(copyAction)

        // Tạo notification channel và xin quyền
        NotificationHelper.createNotificationChannel(this)
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        // Cập nhật lại UI khi quay về
        prefs = AppPreferences(this)
        val today = LocalDate.now()
        setupHeader(today)
        setupCalendar()
        setupNextAlarm()
        checkBatteryOptimization()

        // Đồng bộ trạng thái switch thông báo
        findViewById<SwitchMaterial>(R.id.switchNotificationDrawer).isChecked = prefs.notificationEnabled

        // Đồng bộ trạng thái switch khóa Samsung (Kiểm tra cả quyền vẽ lên ứng dụng khác)
        val hasOverlayPermission = Settings.canDrawOverlays(this)
        if (prefs.autoLockSamsung && !hasOverlayPermission) {
            prefs.autoLockSamsung = false
        }
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchAutoLockSamsung)?.isChecked = prefs.autoLockSamsung

        // Đồng bộ trạng thái switch gộp tháng
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchMergeMonths)?.isChecked = prefs.mergeMonths

        // Cập nhật icon Dark Mode ngoài màn hình chính
        findViewById<ImageButton>(R.id.btnToggleDarkMode)?.let { updateDarkModeIcon(it) }
        updateLegendAndSettingsColors()

        // Tự động kiểm tra cập nhật (1 tháng 1 lần)
        val lastCheck = prefs.lastUpdateCheckTime
        val nowTime = System.currentTimeMillis()
        if (nowTime - lastCheck > 30L * 24 * 60 * 60 * 1000) {
            prefs.lastUpdateCheckTime = nowTime
            checkUpdate(isManual = false)
        }
    }

    // ── Áp dụng theme sáng/tối ──────────────────────────────────────────
    private fun applyTheme() {
        when (prefs.darkMode) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    // ── Thiết lập Menu Drawer (Cài đặt) ──────────────────────────────────
    private fun setupMenuDrawer() {
        // Nút mở Menu trên Header
        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Bỏ qua Tối ưu pin từ Drawer
        findViewById<Button>(R.id.btnBatteryGuide).setOnClickListener {
            triggerBatteryOptimizationSettings()
        }

        // 1. Kíp
        val rgCrew = findViewById<RadioGroup>(R.id.rgCrew)
        when (prefs.selectedCrew) {
            "A" -> findViewById<RadioButton>(R.id.rbCrewA).isChecked = true
            "B" -> findViewById<RadioButton>(R.id.rbCrewB).isChecked = true
            "C" -> findViewById<RadioButton>(R.id.rbCrewC).isChecked = true
            "HC" -> findViewById<RadioButton>(R.id.rbCrewHC).isChecked = true
        }

        rgCrew.setOnCheckedChangeListener { _, checkedId ->
            val crewId = when (checkedId) {
                R.id.rbCrewA -> "A"
                R.id.rbCrewB -> "B"
                R.id.rbCrewC -> "C"
                R.id.rbCrewHC -> "HC"
                else -> "A"
            }
            prefs.selectedCrew = crewId
            onSettingsChanged()
            toggleNightTimeVisibility(crewId == "HC")
        }
        toggleNightTimeVisibility(prefs.selectedCrew == "HC")

        // 2. Giờ thông báo ca Ngày
        val tvDayTime = findViewById<TextView>(R.id.tvDayTime)
        tvDayTime.text = String.format("%02d:%02d", prefs.dayNotificationHour, prefs.dayNotificationMinute)
        findViewById<View>(R.id.layoutDayTime).setOnClickListener {
            showTimePicker(prefs.dayNotificationHour, prefs.dayNotificationMinute, "Giờ thông báo ca Ngày") { h, m ->
                prefs.dayNotificationHour = h
                prefs.dayNotificationMinute = m
                tvDayTime.text = String.format("%02d:%02d", h, m)
                onSettingsChanged()
            }
        }

        // 3. Giờ thông báo ca Đêm
        val tvNightTime = findViewById<TextView>(R.id.tvNightTime)
        tvNightTime.text = String.format("%02d:%02d", prefs.nightNotificationHour, prefs.nightNotificationMinute)
        findViewById<View>(R.id.layoutNightTime).setOnClickListener {
            showTimePicker(prefs.nightNotificationHour, prefs.nightNotificationMinute, "Giờ thông báo ca Đêm") { h, m ->
                prefs.nightNotificationHour = h
                prefs.nightNotificationMinute = m
                tvNightTime.text = String.format("%02d:%02d", h, m)
                onSettingsChanged()
            }
        }

        // 4. Hành động bấm thông báo
        val rgAction = findViewById<RadioGroup>(R.id.rgAction)
        val rbOpenSelf = findViewById<RadioButton>(R.id.rbOpenSelf)
        val rbOpenOther = findViewById<RadioButton>(R.id.rbOpenOther)
        val tilPackage = findViewById<TextInputLayout>(R.id.tilPackage)
        val etPackage = findViewById<TextInputEditText>(R.id.etPackage)

        if (prefs.openOtherApp) {
            rbOpenOther.isChecked = true
            tilPackage.visibility = View.VISIBLE
            if (prefs.targetPackage.isBlank()) {
                prefs.targetPackage = "com.samsung.s1.vselflock"
            }
        } else {
            rbOpenSelf.isChecked = true
            tilPackage.visibility = View.GONE
        }
        etPackage.setText(prefs.targetPackage)
        etPackage.isFocusable = false
        etPackage.isClickable = true
        etPackage.setOnClickListener {
            showAppListDialog(etPackage)
        }

        // Bấm vào lần nữa để thay đổi chọn app khác (khi đã check ON)
        rbOpenOther.setOnClickListener {
            if (prefs.openOtherApp) {
                showAppListDialog(etPackage)
            }
        }

        rgAction.setOnCheckedChangeListener { _, checkedId ->
            val openOther = checkedId == R.id.rbOpenOther
            prefs.openOtherApp = openOther
            tilPackage.visibility = if (openOther) View.VISIBLE else View.GONE
            onSettingsChanged()
            if (openOther) {
                if (prefs.targetPackage.isBlank()) {
                    prefs.targetPackage = "com.samsung.s1.vselflock"
                }
                etPackage.setText(prefs.targetPackage)
            }
        }


        // 4b. Câu thông báo tùy chỉnh
        val etNotificationContent = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNotificationContent)
        etNotificationContent.setText(prefs.notificationContent)
        etNotificationContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.notificationContent = s?.toString()?.trim() ?: ""
            }
        })

        // 4b-2. Thời gian nhắc lại (Snooze)
        val tvSnoozeDuration = findViewById<TextView>(R.id.tvSnoozeDuration)
        tvSnoozeDuration.text = if (prefs.snoozeDuration == 0) "Không" else "${prefs.snoozeDuration} phút"
        findViewById<View>(R.id.layoutSnooze).setOnClickListener {
            val options = arrayOf("Không", "5 phút", "10 phút", "15 phút", "20 phút", "30 phút")
            val values = arrayOf(0, 5, 10, 15, 20, 30)
            
            var currentIdx = values.indexOf(prefs.snoozeDuration)
            if (currentIdx == -1) currentIdx = 2 // Mặc định là 10 phút (index 2)

            android.app.AlertDialog.Builder(this)
                .setTitle("Chọn thời gian nhắc lại")
                .setSingleChoiceItems(options, currentIdx) { dialog, which ->
                    val selectedVal = values[which]
                    prefs.snoozeDuration = selectedVal
                    tvSnoozeDuration.text = if (selectedVal == 0) "Không" else "$selectedVal phút"
                    onSettingsChanged()
                    dialog.dismiss()
                }
                .show()
        }

        // 4c. Tùy chọn màu sắc
        updateColorSelectionIndicators()
        
        // Ca Ngày Click Listeners
        findViewById<View>(R.id.btnColorDay1).setOnClickListener { prefs.dayColor = "#15803D"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorDay2).setOnClickListener { prefs.dayColor = "#EF4444"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorDay3).setOnClickListener { prefs.dayColor = "#F59E0B"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorDay4).setOnClickListener { prefs.dayColor = "#EC4899"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorDay5).setOnClickListener { prefs.dayColor = "#8B5CF6"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorDay6).setOnClickListener { prefs.dayColor = "#F97316"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorDay7).setOnClickListener { prefs.dayColor = "#14B8A6"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorDay8).setOnClickListener { prefs.dayColor = "#06B6D4"; updateColorSelectionIndicators(); onSettingsChanged() }

        // Ca Đêm Click Listeners
        findViewById<View>(R.id.btnColorNight1).setOnClickListener { prefs.nightColor = "#6D28D9"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorNight2).setOnClickListener { prefs.nightColor = "#1E3A8A"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorNight3).setOnClickListener { prefs.nightColor = "#3B82F6"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorNight4).setOnClickListener { prefs.nightColor = "#047857"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorNight5).setOnClickListener { prefs.nightColor = "#475569"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorNight6).setOnClickListener { prefs.nightColor = "#7F1D1D"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorNight7).setOnClickListener { prefs.nightColor = "#9F1239"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorNight8).setOnClickListener { prefs.nightColor = "#0F766E"; updateColorSelectionIndicators(); onSettingsChanged() }

        // Viền HO Click Listeners
        findViewById<View>(R.id.btnColorHo1).setOnClickListener { prefs.hoBorderColor = "#EC4899"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorHo2).setOnClickListener { prefs.hoBorderColor = "#EF4444"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorHo3).setOnClickListener { prefs.hoBorderColor = "#F59E0B"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorHo4).setOnClickListener { prefs.hoBorderColor = "#10B981"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorHo5).setOnClickListener { prefs.hoBorderColor = "#3B82F6"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorHo6).setOnClickListener { prefs.hoBorderColor = "#8B5CF6"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorHo7).setOnClickListener { prefs.hoBorderColor = "#F97316"; updateColorSelectionIndicators(); onSettingsChanged() }
        findViewById<View>(R.id.btnColorHo8).setOnClickListener { prefs.hoBorderColor = "#14B8A6"; updateColorSelectionIndicators(); onSettingsChanged() }

        // 5. About & Update Button
        findViewById<Button>(R.id.btnAbout).setOnClickListener {
            showAboutDialog()
        }
    }

    private fun setupDarkModeToggle() {
        val btnToggleDarkMode = findViewById<ImageButton>(R.id.btnToggleDarkMode)
        updateDarkModeIcon(btnToggleDarkMode)
        btnToggleDarkMode.setOnClickListener {
            val isDark = when (prefs.darkMode) {
                "dark" -> true
                "light" -> false
                else -> {
                    val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
            }
            val newMode = if (isDark) "light" else "dark"
            prefs.darkMode = newMode
            applyTheme()
            updateDarkModeIcon(btnToggleDarkMode)
        }
    }

    private fun updateDarkModeIcon(btn: ImageButton) {
        val isDark = when (prefs.darkMode) {
            "dark" -> true
            "light" -> false
            else -> {
                val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
        if (isDark) {
            btn.setImageResource(R.drawable.ic_light_mode)
            btn.contentDescription = "Chuyển sang chế độ sáng"
        } else {
            btn.setImageResource(R.drawable.ic_dark_mode)
            btn.contentDescription = "Chuyển sang chế độ tối"
        }
    }

    private fun toggleNightTimeVisibility(isHC: Boolean) {
        val layoutNightTime = findViewById<View>(R.id.layoutNightTime)
        layoutNightTime.visibility = if (isHC) View.GONE else View.VISIBLE
    }

    private fun updateColorSelectionIndicators() {
        val dayColors = arrayOf("#15803D", "#EF4444", "#F59E0B", "#EC4899", "#8B5CF6", "#F97316", "#14B8A6", "#06B6D4")
        val dayIds = arrayOf(
            R.id.btnColorDay1, R.id.btnColorDay2, R.id.btnColorDay3, R.id.btnColorDay4,
            R.id.btnColorDay5, R.id.btnColorDay6, R.id.btnColorDay7, R.id.btnColorDay8
        )
        for (i in dayColors.indices) {
            findViewById<View>(dayIds[i]).alpha = if (prefs.dayColor.equals(dayColors[i], ignoreCase = true)) 1.0f else 0.4f
        }

        val nightColors = arrayOf("#6D28D9", "#1E3A8A", "#3B82F6", "#047857", "#475569", "#7F1D1D", "#9F1239", "#0F766E")
        val nightIds = arrayOf(
            R.id.btnColorNight1, R.id.btnColorNight2, R.id.btnColorNight3, R.id.btnColorNight4,
            R.id.btnColorNight5, R.id.btnColorNight6, R.id.btnColorNight7, R.id.btnColorNight8
        )
        for (i in nightColors.indices) {
            findViewById<View>(nightIds[i]).alpha = if (prefs.nightColor.equals(nightColors[i], ignoreCase = true)) 1.0f else 0.4f
        }

        val hoColors = arrayOf("#EC4899", "#EF4444", "#F59E0B", "#10B981", "#3B82F6", "#8B5CF6", "#F97316", "#14B8A6")
        val hoIds = arrayOf(
            R.id.btnColorHo1, R.id.btnColorHo2, R.id.btnColorHo3, R.id.btnColorHo4,
            R.id.btnColorHo5, R.id.btnColorHo6, R.id.btnColorHo7, R.id.btnColorHo8
        )
        for (i in hoColors.indices) {
            findViewById<View>(hoIds[i]).alpha = if (prefs.hoBorderColor.equals(hoColors[i], ignoreCase = true)) 1.0f else 0.4f
        }
    }

    private fun onSettingsChanged() {
        val today = LocalDate.now()
        setupHeader(today)
        setupCalendar()
        setupNextAlarm()
        if (prefs.notificationEnabled) {
            NotificationScheduler.scheduleNext(this)
        }
        updateLegendAndSettingsColors()
    }

    private fun updateLegendAndSettingsColors() {
        try {
            val dayColorVal = Color.parseColor(prefs.dayColor)
            val nightColorVal = Color.parseColor(prefs.nightColor)

            // Đổi màu các ô legend mô tả Ngày, Đêm ngoài màn hình chính
            findViewById<View>(R.id.viewLegendDay)?.setBackgroundColor(dayColorVal)
            findViewById<View>(R.id.viewLegendNight)?.setBackgroundColor(nightColorVal)

            // Đổi màu chữ hiển thị giờ báo thức trong Drawer cho đồng bộ
            findViewById<TextView>(R.id.tvDayTime)?.setTextColor(dayColorVal)
            findViewById<TextView>(R.id.tvNightTime)?.setTextColor(nightColorVal)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── Dialog TimePicker ───────────────────────────────────────────────
    private fun showTimePicker(currentHour: Int, currentMinute: Int, title: String, onTimeSet: (Int, Int) -> Unit) {
        val dialog = TimePickerDialog(this, { _, hourOfDay, minute -> onTimeSet(hourOfDay, minute) }, currentHour, currentMinute, true)
        dialog.setTitle(title)
        dialog.show()
    }

    // ── Header: tên kíp, ca hôm nay, ngày hiện tại ─────────────────────
    private fun setupHeader(today: LocalDate) {
        val crewId = prefs.selectedCrew
        val crew = ShiftCalculator.CREWS.find { it.id == crewId }
        val shiftInfo = ShiftCalculator.getShiftInfo(crewId, today)

        // Badge kíp
        val tvCrewBadge = findViewById<TextView>(R.id.tvCrewBadge)
        tvCrewBadge.text = crew?.name ?: crewId

        // Ca hôm nay
        val tvTodayShift = findViewById<TextView>(R.id.tvTodayShift)
        if (shiftInfo.isHoliday) {
            val label = when (shiftInfo.type) {
                ShiftCalculator.ShiftType.NGAY -> "Ca Ngày ☀️ (HO)"
                ShiftCalculator.ShiftType.DEM -> "Ca Đêm 🌙 (HO)"
                else -> "Nghỉ Lễ 😴"
            }
            tvTodayShift.text = "— $label"
        } else {
            tvTodayShift.text = "— Ca ${shiftInfo.type.label} ${shiftInfo.type.emoji}"
        }

        // Ngày tháng năm
        val tvTodayDate = findViewById<TextView>(R.id.tvTodayDate)
        val dayNames = arrayOf("Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy")
        val dayOfWeek = if (today.dayOfWeek.value == 7) 0 else today.dayOfWeek.value
        tvTodayDate.text = "${dayNames[dayOfWeek]}, ${today.dayOfMonth}/${String.format("%02d", today.monthValue)}/${today.year}"

        // Màu badge theo kíp
        val badgeBg = GradientDrawable().apply { cornerRadius = 40f }
        val crewColor = when (crewId) {
            "A" -> ContextCompat.getColor(this, R.color.crew_a)
            "B" -> ContextCompat.getColor(this, R.color.crew_b)
            "C" -> ContextCompat.getColor(this, R.color.crew_c)
            "HC" -> ContextCompat.getColor(this, R.color.crew_hc)
            else -> ContextCompat.getColor(this, R.color.primary)
        }
        badgeBg.setColor(crewColor)
        tvCrewBadge.background = badgeBg
    }

    // ── Lắng nghe điều khiển lịch ────────────────────────────────────────
    private fun setupCalendarControls() {
        val layoutHideHolidayShift = findViewById<View>(R.id.layoutHideHolidayShift)
        val layoutMergeMonths = findViewById<View>(R.id.layoutMergeMonths)
        val layoutDonation = findViewById<View>(R.id.layoutDonation)
        val switchHideHolidayShift = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchHideHolidayShift)
        val switchMergeMonths = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchMergeMonths)
        val btnToggle = findViewById<ImageButton>(R.id.btnToggleCalendarView)

        // Phục hồi trạng thái ẩn/hiện các option ở vùng cài đặt (layoutMonthBlock2 sẽ do setupCalendar tự quản lý)
        val isVisible = prefs.calendarVisible
        layoutHideHolidayShift.visibility = if (isVisible) View.GONE else View.VISIBLE
        layoutMergeMonths?.visibility = if (isVisible) View.GONE else View.VISIBLE
        layoutDonation?.visibility = View.GONE
        btnToggle.setImageResource(if (isVisible) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down)

        // Phục hồi trạng thái switch ẩn ca Lễ
        switchHideHolidayShift.isChecked = prefs.hideHolidayShift
        switchHideHolidayShift.setOnCheckedChangeListener { _, isChecked ->
            prefs.hideHolidayShift = isChecked
            setupCalendar()
        }

        // Phục hồi trạng thái switch gộp tháng
        switchMergeMonths?.isChecked = prefs.mergeMonths
        switchMergeMonths?.setOnCheckedChangeListener { _, isChecked ->
            prefs.mergeMonths = isChecked
            setupCalendar()
        }

        btnToggle.setOnClickListener {
            val nextState = !prefs.calendarVisible
            prefs.calendarVisible = nextState
            layoutHideHolidayShift.visibility = if (nextState) View.GONE else View.VISIBLE
            layoutMergeMonths?.visibility = if (nextState) View.GONE else View.VISIBLE
            layoutDonation?.visibility = View.GONE
            btnToggle.setImageResource(if (nextState) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down)
            updateMonthDisplay()
            setupCalendar()
        }

        // Đăng ký sự kiện vuốt trên toàn bộ vùng lịch
        val layoutCalendarContent = findViewById<SwipeLinearLayout>(R.id.layoutCalendarContent)
        layoutCalendarContent.onSwipeListener = object : SwipeLinearLayout.OnSwipeListener {
            override fun onSwipeLeft() {
                navigateNextMonth()
            }

            override fun onSwipeRight() {
                navigatePrevMonth()
            }
        }

        // Setup switch Khóa Samsung VSelf Lock
        val switchAutoLockSamsung = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchAutoLockSamsung)
        switchAutoLockSamsung.isChecked = prefs.autoLockSamsung
        switchAutoLockSamsung.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Bước 1: Kiểm tra VSelfLock đã cài đặt chưa
                if (!SamsungLockHelper.isVSelfLockInstalled(this)) {
                    AlertDialog.Builder(this)
                        .setTitle("Chưa cài đặt VSelfLock")
                        .setMessage("Tính năng auto MDM yêu cầu app Samsung VSelfLock đã được cài đặt trên thiết bị. Vui lòng cài đặt app trước khi bật tính năng này.")
                        .setPositiveButton("Đã hiểu") { dialog, _ ->
                            dialog.dismiss()
                            switchAutoLockSamsung.isChecked = false
                        }
                        .setCancelable(false)
                        .show()
                    return@setOnCheckedChangeListener
                }

                // Bước 2: Kiểm tra quyền Overlay (cần cho background lock)
                if (!Settings.canDrawOverlays(this)) {
                    AlertDialog.Builder(this)
                        .setTitle("Cần cấp quyền hệ thống")
                        .setMessage("Để tự động gửi tín hiệu khóa Samsung VSelf Lock khi báo thức chạy nền hoặc khi màn hình đang tắt, ứng dụng cần quyền 'Xuất hiện trên cùng'. Vui lòng cấp quyền này ở màn hình tiếp theo.")
                        .setPositiveButton("Cấp quyền") { dialog, _ ->
                            dialog.dismiss()
                            try {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(this, "Không thể mở màn hình cài đặt quyền", Toast.LENGTH_LONG).show()
                            }
                        }
                        .setNegativeButton("Để sau") { dialog, _ ->
                            dialog.dismiss()
                            switchAutoLockSamsung.isChecked = false
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    prefs.autoLockSamsung = true
                    Toast.makeText(this, "Đã bật auto MDM", Toast.LENGTH_SHORT).show()
                }
            } else {
                prefs.autoLockSamsung = false
            }
        }
    }

    // ── Điều hướng tháng trước / tháng sau ──────────────────────────────
    private fun navigatePrevMonth() {
        if (currentMonth == 1) { currentMonth = 12; currentYear-- }
        else currentMonth--
        updateMonthDisplay()
        setupCalendar()
    }

    private fun navigateNextMonth() {
        if (currentMonth == 12) { currentMonth = 1; currentYear++ }
        else currentMonth++
        updateMonthDisplay()
        setupCalendar()
    }

    private fun setupMonthNavigation() {
        findViewById<ImageButton>(R.id.btnPrevMonth).setOnClickListener {
            navigatePrevMonth()
        }

        findViewById<ImageButton>(R.id.btnNextMonth).setOnClickListener {
            navigateNextMonth()
        }

        updateMonthDisplay()
    }

    private fun updateMonthDisplay() {
        val monthNames = arrayOf(
            "Tháng 1", "Tháng 2", "Tháng 3", "Tháng 4", "Tháng 5", "Tháng 6",
            "Tháng 7", "Tháng 8", "Tháng 9", "Tháng 10", "Tháng 11", "Tháng 12"
        )
        val isVisible = prefs.calendarVisible
        val displayStr = if (isVisible) {
            val nextMonth = if (currentMonth == 12) 1 else currentMonth + 1
            val nextYear = if (currentMonth == 12) currentYear + 1 else currentYear
            if (currentYear == nextYear) {
                "${monthNames[currentMonth - 1]} - ${nextMonth} / $currentYear"
            } else {
                "${monthNames[currentMonth - 1]}/$currentYear - ${nextMonth}/$nextYear"
            }
        } else {
            "${monthNames[currentMonth - 1]} / $currentYear"
        }
        findViewById<TextView>(R.id.tvMonthYear).text = displayStr
    }

    // ── Helper điền tiêu đề Thứ (T2..CN) ─────────────────────────────
    private fun populateDayOfWeekHeaders(layout: LinearLayout, density: Float) {
        layout.removeAllViews()
        val dayHeaders = arrayOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
        for (header in dayHeaders) {
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = header
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(
                    ContextCompat.getColor(this@MainActivity,
                        if (header == "CN" || header == "T7") R.color.shift_holiday else R.color.on_surface_variant
                    )
                )
                setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
                typeface = Typeface.DEFAULT_BOLD
            }
            layout.addView(tv)
        }
    }

    // ── Render lịch (Gộp hoặc Tách 2 tháng) ───────────────────────────
    private fun setupCalendar() {
        val crewId = prefs.selectedCrew
        val today = LocalDate.now()
        val density = resources.displayMetrics.density
        val cellHeight = (36 * density).toInt()

        // Luôn hiển thị Month Navigation
        findViewById<View>(R.id.layoutMonthNavigation).visibility = View.VISIBLE

        val isVisible = prefs.calendarVisible
        val merge = prefs.mergeMonths

        val layoutMonthBlock2 = findViewById<View>(R.id.layoutMonthBlock2)
        val tvMonthYear1 = findViewById<TextView>(R.id.tvMonthYear1)
        val tvMonthYear2 = findViewById<TextView>(R.id.tvMonthYear2)
        val grid1 = findViewById<GridLayout>(R.id.calendarGrid1)
        val grid2 = findViewById<GridLayout>(R.id.calendarGrid2)

        grid1.removeAllViews()
        grid2.removeAllViews()

        // Thiết lập header thứ cho tháng 1
        val headerLayout1 = findViewById<LinearLayout>(R.id.dayOfWeekHeaders1)
        populateDayOfWeekHeaders(headerLayout1, density)

        val firstDay1 = LocalDate.of(currentYear, currentMonth, 1)
        val daysInMonth1 = firstDay1.lengthOfMonth()
        val startOffset1 = firstDay1.dayOfWeek.value - 1

        // 1. Ô trống đầu tháng 1
        for (i in 0 until startOffset1) {
            val emptyView = View(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0; height = cellHeight
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                }
            }
            grid1.addView(emptyView)
        }

        // 2. Các ngày tháng 1
        for (day in 1..daysInMonth1) {
            val date = LocalDate.of(currentYear, currentMonth, day)
            val cell = createCellView(crewId, date, today, cellHeight, density, false)
            grid1.addView(cell)
        }

        // 3. Xử lý tháng thứ 2 khi mở rộng
        if (isVisible) {
            val nextMonth = if (currentMonth == 12) 1 else currentMonth + 1
            val nextYear = if (currentMonth == 12) currentYear + 1 else currentYear
            val firstDay2 = LocalDate.of(nextYear, nextMonth, 1)
            val daysInMonth2 = firstDay2.lengthOfMonth()

            if (merge) {
                // CHẾ ĐỘ GỘP (Khối 2 ẩn, vẽ chung vào grid 1)
                layoutMonthBlock2.visibility = View.GONE
                tvMonthYear1.visibility = View.GONE

                for (day in 1..daysInMonth2) {
                    val date = LocalDate.of(nextYear, nextMonth, day)
                    val cell = createCellView(crewId, date, today, cellHeight, density, false)
                    grid1.addView(cell)
                }
            } else {
                // CHẾ ĐỘ TÁCH (Khối 2 hiện, có dòng Thứ & Title riêng)
                layoutMonthBlock2.visibility = View.VISIBLE
                tvMonthYear1.visibility = View.VISIBLE

                val monthNames = arrayOf(
                    "Tháng 1", "Tháng 2", "Tháng 3", "Tháng 4", "Tháng 5", "Tháng 6",
                    "Tháng 7", "Tháng 8", "Tháng 9", "Tháng 10", "Tháng 11", "Tháng 12"
                )
                tvMonthYear1.text = "${monthNames[currentMonth - 1]} / $currentYear"
                tvMonthYear2.text = "${monthNames[nextMonth - 1]} / $nextYear"

                // Thiết lập header thứ cho tháng 2
                val headerLayout2 = findViewById<LinearLayout>(R.id.dayOfWeekHeaders2)
                populateDayOfWeekHeaders(headerLayout2, density)

                // Ô trống đầu tháng 2
                val startOffset2 = firstDay2.dayOfWeek.value - 1
                for (i in 0 until startOffset2) {
                    val emptyView = View(this).apply {
                        layoutParams = GridLayout.LayoutParams().apply {
                            width = 0; height = cellHeight
                            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                        }
                    }
                    grid2.addView(emptyView)
                }

                // Các ngày tháng 2
                for (day in 1..daysInMonth2) {
                    val date = LocalDate.of(nextYear, nextMonth, day)
                    val cell = createCellView(crewId, date, today, cellHeight, density, false)
                    grid2.addView(cell)
                }
            }
        } else {
            // Chỉ hiển thị 1 tháng
            layoutMonthBlock2.visibility = View.GONE
            tvMonthYear1.visibility = View.GONE
        }

        // Cập nhật thống kê HO của cả 3 kíp
        setupHOStats()
    }

    /**
     * Hiển thị thống kê HO cho cả 3 kíp và tổng số ngày lễ trong năm
     */
    private fun setupHOStats() {
        val tvTitle = findViewById<TextView>(R.id.tvHOStatsTitle)
        val tvContent = findViewById<TextView>(R.id.tvHOStatsContent)
        val today = LocalDate.now()
        
        val statsA = ShiftCalculator.getHOStatsForYear("A", currentYear, today)
        val statsB = ShiftCalculator.getHOStatsForYear("B", currentYear, today)
        val statsC = ShiftCalculator.getHOStatsForYear("C", currentYear, today)
        val totalHolidays = ShiftCalculator.getHolidayCountForYear(currentYear)
        
        tvTitle.text = "HO $currentYear (Lễ:$totalHolidays)"
        tvContent.text = "A: ${statsA.total} (Còn:${statsA.remaining})\nB: ${statsB.total} (Còn:${statsB.remaining})\nC: ${statsC.total} (Còn:${statsC.remaining})"
    }

    private fun createCellView(
        crewId: String,
        date: LocalDate,
        today: LocalDate,
        cellHeight: Int,
        density: Float,
        fadePast: Boolean
    ): FrameLayout {
        val shiftInfo = ShiftCalculator.getShiftInfo(crewId, date)
        val isToday = date == today
        val isPast = date.isBefore(today)
        val isOfficialHoliday = ShiftCalculator.isHoliday(date)

        val cellLayout = FrameLayout(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0; height = cellHeight
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins((2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt())
            }
            if (fadePast && isPast) {
                alpha = 0.45f
            }
        }

        // Lấy màu sắc tùy biến từ Prefs
        val dayColorVal = Color.parseColor(prefs.dayColor)
        val dayBgColorVal = Color.argb(0x22, Color.red(dayColorVal), Color.green(dayColorVal), Color.blue(dayColorVal))

        val nightColorVal = Color.parseColor(prefs.nightColor)
        val nightBgColorVal = Color.argb(0x22, Color.red(nightColorVal), Color.green(nightColorVal), Color.blue(nightColorVal))

        // Tính màu nền, màu chữ, nhãn ca
        val bgColor: Int
        val textColor: Int
        val labelText: String
        val labelColor: Int

        if (isOfficialHoliday) {
            bgColor = ContextCompat.getColor(this, R.color.shift_holiday_bg)
            textColor = if (isToday) {
                ContextCompat.getColor(this, R.color.primary)
            } else {
                ContextCompat.getColor(this, R.color.shift_holiday)
            }
            val holName = ShiftCalculator.getHolidayName(date)
            labelText = when {
                holName == null -> "Lễ"
                holName.contains("Tết") -> "Tết"
                holName.contains("SDV") -> "SDV"
                else -> "Lễ"
            }
            labelColor = ContextCompat.getColor(this, R.color.shift_holiday)
        } else {
            bgColor = when (shiftInfo.type) {
                ShiftCalculator.ShiftType.NGAY -> dayBgColorVal
                ShiftCalculator.ShiftType.DEM -> nightBgColorVal
                else -> ContextCompat.getColor(this, R.color.shift_off_bg)
            }
            textColor = when {
                isToday -> ContextCompat.getColor(this, R.color.primary)
                shiftInfo.type == ShiftCalculator.ShiftType.NGAY -> dayColorVal
                shiftInfo.type == ShiftCalculator.ShiftType.DEM -> nightColorVal
                else -> ContextCompat.getColor(this, R.color.on_background)
            }
            labelText = if (shiftInfo.isHoliday) {
                when (shiftInfo.type) {
                    ShiftCalculator.ShiftType.NGAY -> "HO N"
                    ShiftCalculator.ShiftType.DEM -> "HO Đ"
                    else -> "−"
                }
            } else {
                when (shiftInfo.type) {
                    ShiftCalculator.ShiftType.NGAY -> "N"
                    ShiftCalculator.ShiftType.DEM -> "Đ"
                    else -> "−"
                }
            }
            labelColor = when (shiftInfo.type) {
                ShiftCalculator.ShiftType.NGAY -> dayColorVal
                ShiftCalculator.ShiftType.DEM -> nightColorVal
                else -> ContextCompat.getColor(this, R.color.shift_off)
            }
        }

        val bgDrawable = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = 6f * density
            if (isToday) {
                setStroke((2 * density).toInt(), ContextCompat.getColor(this@MainActivity, R.color.today_ring))
            } else if (!isOfficialHoliday && shiftInfo.isHoliday) {
                val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val strokePx = if (isDarkMode) {
                    (0.5f * density).toInt().coerceAtLeast(1)
                } else {
                    Math.round(0.65f * density).coerceAtLeast(1)
                }
                val hoColorHex = prefs.hoBorderColor
                val hoColorVal = Color.parseColor(hoColorHex)
                val hoColorWithAlpha = Color.argb(204, Color.red(hoColorVal), Color.green(hoColorVal), Color.blue(hoColorVal))
                setStroke(strokePx, hoColorWithAlpha)
            }
        }
        cellLayout.background = bgDrawable

        // Container chứa số ngày ở trên và ca thường ở dưới (sát mép trái)
        val leftContainer = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.START or Gravity.TOP
                leftMargin = (5 * density).toInt()
                topMargin = (1 * density).toInt()
            }
            orientation = LinearLayout.VERTICAL
        }

        // Số ngày (Góc trên trái của container)
        val dayText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = if (date.dayOfMonth == 1) "1/${date.monthValue}" else date.dayOfMonth.toString()
            textSize = 12f
            setTextColor(textColor)
        }

        // Nhãn ca Ngày/Đêm của ca trực dưới số ngày (sát mép trái)
        val shiftLabelBottom = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (-1 * density).toInt() // Kéo gần lại số ngày
            }
            text = if (isOfficialHoliday && prefs.hideHolidayShift) {
                "" // Ẩn ca Ngày/Đêm trên ngày lễ khi cài đặt bật
            } else {
                when (shiftInfo.type) {
                    ShiftCalculator.ShiftType.NGAY -> "Ngày"
                    ShiftCalculator.ShiftType.DEM -> "Đêm"
                    else -> ""
                }
            }
            textSize = 8.5f
            setTextColor(labelColor)
        }

        // Nhãn ca/HO/Lễ (Góc trên cùng bên phải ô lịch)
        val shiftLabelTop = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END or Gravity.TOP
                rightMargin = (5 * density).toInt()
                topMargin = (2 * density).toInt()
            }
            text = if (isOfficialHoliday) {
                labelText
            } else if (shiftInfo.isHoliday) {
                "HO"
            } else {
                ""
            }
            textSize = 8.5f
            setTextColor(labelColor)
        }

        // Tính ngày âm lịch sử dụng VietCalendar
        val lunar = VietCalendar.convertSolar2Lunar(date.dayOfMonth, date.monthValue, date.year, 7.0)
        val lunarDay = lunar[0]
        val lunarMonth = lunar[1]
        val lunarIsLeap = lunar[3]
        val lunarText = if (lunarDay == 1) {
            val leapSuffix = if (lunarIsLeap == 1) "t" else ""
            "1/$lunarMonth$leapSuffix"
        } else {
            lunarDay.toString()
        }

        // Nhãn ngày âm lịch (Góc dưới cùng bên phải)
        val tvLunar = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END or Gravity.BOTTOM
                rightMargin = (5 * density).toInt()
                bottomMargin = (1 * density).toInt()
            }
            text = lunarText
            textSize = if (lunarDay == 1) 7.0f else 7.5f // Cỡ chữ nhỏ hơn cho mùng 1 âm để không bị tràn ô
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.on_surface_variant))
            if (lunarDay == 1) {
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.primary)) // In đậm và đổi màu mùng 1 âm
            }
        }

        // Kiểm tra in đậm nếu là ngày HO hoặc lễ tế hoặc hôm nay
        val isBold = labelText.contains("HO") || isOfficialHoliday
        if (isBold || isToday) {
            dayText.typeface = Typeface.DEFAULT_BOLD
            shiftLabelTop.typeface = Typeface.DEFAULT_BOLD
            shiftLabelBottom.typeface = Typeface.DEFAULT_BOLD
        }

        leftContainer.addView(dayText)
        leftContainer.addView(shiftLabelBottom)
        cellLayout.addView(leftContainer)
        cellLayout.addView(shiftLabelTop)
        cellLayout.addView(tvLunar)

        // Bấm vào ô -> hiện Toast thông tin ca chi tiết
        cellLayout.setOnClickListener {
            val info = if (isOfficialHoliday) {
                val subLabel = when (shiftInfo.type) {
                    ShiftCalculator.ShiftType.NGAY -> "Lễ ca Ngày (HO)"
                    ShiftCalculator.ShiftType.DEM -> "Lễ ca Đêm (HO)"
                    else -> "Nghỉ lễ"
                }
                "${shiftInfo.holidayName} (${date.dayOfMonth}/${date.monthValue}): $subLabel"
            } else {
                "${shiftInfo.type.emoji} Ca ${shiftInfo.type.label} (${date.dayOfMonth}/${date.monthValue})"
            }
            Toast.makeText(this, info, Toast.LENGTH_SHORT).show()
        }

        return cellLayout
    }

    // ── Hiển thị thông tin alarm tiếp theo ──────────────────────────────
    private fun setupNextAlarm() {
        val tvNextAlarm = findViewById<TextView>(R.id.tvNextAlarm)
        val crewId = prefs.selectedCrew
        val now = LocalDate.now()
        val timeNow = java.time.LocalDateTime.now()

        if (!prefs.notificationEnabled) {
            tvNextAlarm.text = "🔔 Đã tắt"
            return
        }

        // Tìm ngày làm việc tiếp theo trong 14 ngày tới
        for (dayOffset in 0..14) {
            val date = now.plusDays(dayOffset.toLong())
            val shift = ShiftCalculator.getActualShift(crewId, date)
            if (shift != ShiftCalculator.ShiftType.NGHI) {
                val hour = if (shift == ShiftCalculator.ShiftType.NGAY) prefs.dayNotificationHour else prefs.nightNotificationHour
                val minute = if (shift == ShiftCalculator.ShiftType.NGAY) prefs.dayNotificationMinute else prefs.nightNotificationMinute
                
                val alarmDateTime = date.atTime(hour, minute)
                if (alarmDateTime.isAfter(timeNow)) {
                    val dayNames = arrayOf("CN", "T2", "T3", "T4", "T5", "T6", "T7")
                    val dow = if (date.dayOfWeek.value == 7) 0 else date.dayOfWeek.value
                    val timeStr = String.format("%02d:%02d", hour, minute)
                    val isHol = ShiftCalculator.isHoliday(date)
                    val labelSuffix = if (isHol) " (HO Lễ)" else ""
                    tvNextAlarm.text = "🔔 $timeStr - ${dayNames[dow]}, ${date.dayOfMonth}/${date.monthValue} (${shift.emoji}$labelSuffix)"
                    return
                }
            }
        }
        tvNextAlarm.text = "🔔 Không có ca trực trong 14 ngày"
    }

    // ── Toggle bật/tắt thông báo (Chỉ trong settings Drawer) ───────────────
    private fun setupNotificationToggle() {
        val switchDrawer = findViewById<SwitchMaterial>(R.id.switchNotificationDrawer)
        switchDrawer.isChecked = prefs.notificationEnabled

        switchDrawer.setOnCheckedChangeListener { _, isChecked ->
            prefs.notificationEnabled = isChecked
            if (isChecked) {
                NotificationScheduler.scheduleNext(this)
            } else {
                NotificationScheduler.cancelAlarm(this)
            }
            setupNextAlarm()
        }
    }



    // ── Battery Optimization Check & Request ─────────────────────────────
    private fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER
        return manufacturer.contains("xiaomi", ignoreCase = true) ||
               manufacturer.contains("redmi", ignoreCase = true) ||
               manufacturer.contains("poco", ignoreCase = true)
    }

    private fun isXiaomiOpsPermissionAllowed(op: Int): Boolean {
        return try {
            val appOpsManager = getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val method = android.app.AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            val mode = method.invoke(
                appOpsManager,
                op,
                android.os.Process.myUid(),
                packageName
            ) as Int
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            true
        }
    }

    private fun checkBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(packageName)
        
        val tvTitle = findViewById<TextView>(R.id.tvBatteryWarningTitle)
        val tvContent = findViewById<TextView>(R.id.tvBatteryWarningContent)
        
        if (isXiaomiDevice()) {
            val isShowWhenLockedAllowed = isXiaomiOpsPermissionAllowed(10020)
            val isBgStartAllowed = isXiaomiOpsPermissionAllowed(10021)
            
            val needsWarning = !isIgnoring || !isShowWhenLockedAllowed || !isBgStartAllowed
            if (needsWarning) {
                cardBatteryWarning.visibility = View.VISIBLE
                findViewById<Button>(R.id.btnBatteryGuide).visibility = View.VISIBLE
                
                tvTitle.text = "⚠️ Yêu cầu cấp quyền Xiaomi"
                val sb = java.lang.StringBuilder()
                if (!isIgnoring) {
                    sb.append("- Chưa tắt tối ưu hóa pin (Pin có thể bị đóng băng).\n")
                }
                if (!isShowWhenLockedAllowed || !isBgStartAllowed) {
                    sb.append("- Thiếu quyền hiển thị trên màn hình khóa hoặc chạy nền.\n")
                }
                sb.append("Vui lòng bấm 'Cấu hình ngay' để thiết lập đầy đủ, giúp báo thức sáng màn hình khi reo.")
                tvContent.text = sb.toString()
            } else {
                cardBatteryWarning.visibility = View.GONE
                findViewById<Button>(R.id.btnBatteryGuide).visibility = View.GONE
            }
        } else {
            cardBatteryWarning.visibility = if (isIgnoring) View.GONE else View.VISIBLE
            findViewById<Button>(R.id.btnBatteryGuide).visibility = if (isIgnoring) View.GONE else View.VISIBLE
            
            if (!isIgnoring) {
                if (Build.MANUFACTURER.contains("samsung", ignoreCase = true)) {
                    tvTitle.text = "⚠️ Tối ưu pin Samsung"
                    tvContent.text = "Để báo thức hoạt động ổn định trên Samsung, vui lòng tắt tối ưu hóa pin (chọn Không hạn chế) cho ứng dụng này."
                } else {
                    tvTitle.text = "⚠️ Cảnh báo tối ưu pin!"
                    tvContent.text = "Thiết bị có thể chặn thông báo chạy ngầm. Hãy tắt tối ưu hóa pin cho ứng dụng này để đảm bảo chuông báo thức hoạt động ổn định."
                }
            }
        }
    }

    private fun setupBatteryOptimizationButtons() {
        findViewById<Button>(R.id.btnDisableBatteryOptimization).setOnClickListener {
            triggerBatteryOptimizationSettings()
        }
    }

    private fun triggerBatteryOptimizationSettings() {
        if (isXiaomiDevice()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Cấu hình quyền Xiaomi (MIUI/HyperOS)")
                .setMessage("Để báo thức sáng màn hình và đổ chuông ổn định trên Xiaomi, vui lòng thực hiện đúng 3 bước sau:\n\n" +
                        "1. Nhấn nút 'Đi đến Cài đặt' dưới đây.\n" +
                        "2. Chọn mục **'Quyền khác'** (Other permissions).\n" +
                        "3. Bật (Cho phép hiển thị màu xanh) các quyền sau:\n" +
                        "   - **Hiển thị trên màn hình khóa** (Show on Lock screen)\n" +
                        "   - **Bắt đầu trong nền** (Start in background)\n" +
                        "   - **Hiển thị cửa sổ pop-up khi chạy trong nền** (Display pop-up windows)\n\n" +
                        "Đồng thời tại mục **'Tiết kiệm pin'** (Battery saver), chọn **'Không hạn chế'** (No restrictions).")
                .setPositiveButton("Đi đến Cài đặt") { _, _ ->
                    openAppDetailsSettings()
                }
                .setNegativeButton("Đóng", null)
                .show()
        } else if (Build.MANUFACTURER.contains("samsung", ignoreCase = true)) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Cấu hình tối ưu pin Samsung (One UI)")
                .setMessage("Để chuông báo thức KHÔNG bị trễ hoặc tắt trên máy Samsung, vui lòng:\n\n" +
                        "1. Nhấn nút 'Đi đến Cài đặt' bên dưới.\n" +
                        "2. Chọn mục 'Pin' (Battery).\n" +
                        "3. Chọn 'Không hạn chế' (Unrestricted).\n\n" +
                        "Đồng thời đảm bảo app không bị đưa vào danh sách 'Ứng dụng ngủ sâu' (Deep sleeping apps) trong mục Chăm sóc thiết bị.")
                .setPositiveButton("Đi đến Cài đặt") { _, _ ->
                    openAppDetailsSettings()
                }
                .setNegativeButton("Đóng", null)
                .show()
        } else {
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:$packageName")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback mở danh sách chung nếu bị chặn
                val intentSettings = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                try {
                    startActivity(intentSettings)
                } catch (ex: Exception) {
                    openAppDetailsSettings()
                }
            }
        }
    }

    private fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể mở cài đặt ứng dụng", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Xin quyền POST_NOTIFICATIONS (Android 13+) ──────────────────────
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                NotificationScheduler.scheduleNext(this)
            }
        } else {
            NotificationScheduler.scheduleNext(this)
        }
    }

    private fun showAppListDialog(editText: EditText) {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        
        class AppInfoItem(
            val label: String,
            val packageName: String
        )
        
        val appsList = resolveInfos.map { info ->
            val label = info.loadLabel(pm).toString()
            val packageName = info.activityInfo.packageName
            AppInfoItem(label, packageName)
        }.sortedBy { it.label }
        
        val appNames = appsList.map { "${it.label} (${it.packageName})" }.toTypedArray()
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Chọn ứng dụng cần mở")
            .setItems(appNames) { dialog, which ->
                val selectedApp = appsList[which]
                editText.setText(selectedApp.packageName)
                prefs.targetPackage = selectedApp.packageName
                onSettingsChanged()
                dialog.dismiss()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showAboutDialog() {
        val currentVerName = (try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: java.lang.Exception) {
            "1.0"
        }) ?: "1.0"
        android.app.AlertDialog.Builder(this)
            .setTitle("Giới thiệu ứng dụng")
            .setMessage("Ứng dụng Lịch Kíp SDV giúp theo dõi lịch trực ca và nhắc nhở công việc.\n\n" +
                    "Phát triển bởi: vanlinh.vu\n" +
                    "Bản quyền © 2026\n" +
                    "Phiên bản hiện tại: v$currentVerName")
            .setPositiveButton("Kiểm tra cập nhật") { _, _ ->
                checkUpdate(isManual = true)
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun checkUpdate(isManual: Boolean) {
        val progressDialog = if (isManual) {
            @Suppress("DEPRECATION")
            android.app.ProgressDialog(this).apply {
                setMessage("Đang kiểm tra cập nhật...")
                setCancelable(false)
                show()
            }
        } else null

        Thread {
            try {
                val url = java.net.URL("https://api.github.com/repos/vanlinh0392-art/LichKipSDV/releases/latest")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "LichKipSDVApp")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(response)
                    val tagName = json.getString("tag_name")
                    val assets = json.getJSONArray("assets")
                    var downloadUrl = ""
                    if (assets.length() > 0) {
                        downloadUrl = assets.getJSONObject(0).getString("browser_download_url")
                    }
                    val body = json.optString("body", "")
                    
                    val currentVerName = (try {
                        packageManager.getPackageInfo(packageName, 0).versionName
                    } catch (e: java.lang.Exception) {
                        "1.0"
                    }) ?: "1.0"
                    
                    val hasNewVersion = isNewerVersion(currentVerName, tagName)
                    
                    runOnUiThread {
                        progressDialog?.dismiss()
                        if (hasNewVersion && downloadUrl.isNotBlank()) {
                            showNewVersionDialog(tagName, body, downloadUrl)
                        } else {
                            if (isManual) {
                                Toast.makeText(this@MainActivity, "Bạn đang sử dụng phiên bản mới nhất (v$currentVerName).", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    runOnUiThread {
                        progressDialog?.dismiss()
                        if (isManual) {
                            Toast.makeText(this@MainActivity, "Không thể kết nối máy chủ GitHub. Mã lỗi: ${conn.responseCode}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                conn.disconnect()
            } catch (e: java.lang.Exception) {
                runOnUiThread {
                    progressDialog?.dismiss()
                    if (isManual) {
                        Toast.makeText(this@MainActivity, "Lỗi kiểm tra cập nhật: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    private fun isNewerVersion(current: String, target: String): Boolean {
        val curClean = current.replace("v", "", ignoreCase = true).trim()
        val tarClean = target.replace("v", "", ignoreCase = true).trim()
        
        val curParts = curClean.split(".")
        val tarParts = tarClean.split(".")
        
        val length = maxOf(curParts.size, tarParts.size)
        for (i in 0 until length) {
            val curVal = if (i < curParts.size) curParts[i].toIntOrNull() ?: 0 else 0
            val tarVal = if (i < tarParts.size) tarParts[i].toIntOrNull() ?: 0 else 0
            if (tarVal > curVal) return true
            if (curVal > tarVal) return false
        }
        return false
    }

    private fun showNewVersionDialog(version: String, notes: String, downloadUrl: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Phát hiện phiên bản mới $version")
            .setMessage("Có phiên bản mới ($version) trên GitHub.\n\n" +
                    "Nội dung cập nhật:\n$notes\n\n" +
                    "Bạn có muốn tải về bản cập nhật ngay bây giờ?")
            .setPositiveButton("Tải về") { _, _ ->
                downloadAndInstallApk(downloadUrl, version)
            }
            .setNegativeButton("Để sau", null)
            .show()
    }

    private fun downloadAndInstallApk(url: String, version: String) {
        try {
            val destinationFile = java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "LichKipSDV_$version.apk")
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            
            val manager = getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
            val request = android.app.DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("Tải về Lịch Kíp SDV $version")
                setDescription("Đang tải xuống phiên bản cập nhật...")
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destinationFile))
            }
            
            val downloadId = manager.enqueue(request)
            Toast.makeText(this, "Bắt đầu tải xuống bản cập nhật...", Toast.LENGTH_LONG).show()
            
            val onComplete = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                    val id = intent?.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (id == downloadId) {
                        unregisterReceiver(this)
                        installApk(version)
                    }
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    onComplete, 
                    android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    android.content.Context.RECEIVER_EXPORTED
                )
            } else {
                registerReceiver(
                    onComplete, 
                    android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
        } catch (e: java.lang.Exception) {
            Toast.makeText(this, "Lỗi tải bản cập nhật: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun installApk(version: String) {
        try {
            val file = java.io.File(
                getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),
                "LichKipSDV_$version.apk"
            )
            if (!file.exists()) {
                Toast.makeText(this, "Không tìm thấy file APK đã tải", Toast.LENGTH_SHORT).show()
                return
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                val apkUri = androidx.core.content.FileProvider.getUriForFile(
                    this@MainActivity,
                    "$packageName.fileprovider",
                    file
                )
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: java.lang.Exception) {
            Toast.makeText(this, "Lỗi mở file cài đặt: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun changeToNextCrew() {
        val currentCrew = prefs.selectedCrew
        val nextCrew = when (currentCrew) {
            "A" -> "B"
            "B" -> "C"
            "C" -> "HC"
            else -> "A"
        }
        prefs.selectedCrew = nextCrew
        
        onSettingsChanged()
        updateDrawerCrewSelection()
        
        Toast.makeText(this, "Đã đổi sang Kíp $nextCrew", Toast.LENGTH_SHORT).show()
    }

    private fun updateDrawerCrewSelection() {
        val rgCrew = findViewById<RadioGroup>(R.id.rgCrew) ?: return
        rgCrew.setOnCheckedChangeListener(null)
        
        when (prefs.selectedCrew) {
            "A" -> findViewById<RadioButton>(R.id.rbCrewA)?.isChecked = true
            "B" -> findViewById<RadioButton>(R.id.rbCrewB)?.isChecked = true
            "C" -> findViewById<RadioButton>(R.id.rbCrewC)?.isChecked = true
            "HC" -> findViewById<RadioButton>(R.id.rbCrewHC)?.isChecked = true
        }
        
        rgCrew.setOnCheckedChangeListener { _, checkedId ->
            val crewId = when (checkedId) {
                R.id.rbCrewA -> "A"
                R.id.rbCrewB -> "B"
                R.id.rbCrewC -> "C"
                R.id.rbCrewHC -> "HC"
                else -> "A"
            }
            prefs.selectedCrew = crewId
            onSettingsChanged()
            toggleNightTimeVisibility(crewId == "HC")
        }
        toggleNightTimeVisibility(prefs.selectedCrew == "HC")
    }

    private fun setupCrewBadgeDoubleClick() {
        val tvCrewBadge = findViewById<TextView>(R.id.tvCrewBadge) ?: return
        var lastClickTime: Long = 0
        tvCrewBadge.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 300) {
                changeToNextCrew()
                lastClickTime = 0
            } else {
                lastClickTime = currentTime
            }
        }
    }
}
