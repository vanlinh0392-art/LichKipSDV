package com.sdv.lichnoti

import java.time.LocalDate

object ShiftCalculator {

    enum class ShiftType(val code: String, val label: String, val emoji: String) {
        NGAY("NGAY", "Ngày", "☀️"),
        DEM("DEM", "Đêm", "🌙"),
        NGHI("NGHI", "Nghỉ", "😴")
    }

    data class CrewInfo(
        val id: String,
        val name: String,
        val offset: Int
    )

    // 12-day cycle: 4 Day, 2 Off, 4 Night, 2 Off
    private val CYCLE = arrayOf(
        ShiftType.NGAY, ShiftType.NGAY, ShiftType.NGAY, ShiftType.NGAY,
        ShiftType.NGHI, ShiftType.NGHI,
        ShiftType.DEM, ShiftType.DEM, ShiftType.DEM, ShiftType.DEM,
        ShiftType.NGHI, ShiftType.NGHI
    )

    // Anchor date: January 3, 2026
    private val ANCHOR_DATE = LocalDate.of(2026, 1, 3)
    private val ANCHOR_JDN = toJulianDayNumber(ANCHOR_DATE)

    // Crew definitions with offsets from lichkipsdv.com
    val CREWS = listOf(
        CrewInfo("A", "Kíp A", 0),
        CrewInfo("B", "Kíp B", 8),
        CrewInfo("C", "Kíp C", 4),
        CrewInfo("HC", "Hành Chính", -1)
    )

    data class ShiftInfo(
        val type: ShiftType,
        val isHoliday: Boolean,
        val holidayName: String?
    )

    data class HOStats(
        val total: Int,
        val remaining: Int
    )

    /**
     * Kiểm tra Thứ Bảy có phải ngày HO hay không:
     * - Trước tháng 7/2026: Thứ Bảy thứ nhất và thứ ba của tháng là HO
     * - Từ tháng 7-12/2026: Chỉ Thứ Bảy thứ nhất của tháng là HO (cắt ngày 18/7 và tuần 3)
     * - Từ năm 2027 trở đi: Không còn Thứ Bảy HO (chỉ còn HO Chủ Nhật)
     */
    private fun isSaturdayHO(date: LocalDate): Boolean {
        if (date.dayOfWeek.value != 6) return false
        val y = date.year
        val m = date.monthValue
        val nthSaturday = (date.dayOfMonth - 1) / 7 + 1

        return when {
            y > 2026 -> false
            y == 2026 && m >= 7 -> nthSaturday == 1
            else -> nthSaturday == 1 || nthSaturday == 3
        }
    }

    /**
     * Tính toán thống kê số ngày HO của một kíp trong năm.
     * Khớp 100% logic trang gốc:
     * - Ngày lễ: chỉ tính HO cho kíp đi làm ca Ngày (ca gốc Ngày).
     * - Ngày thường: tính HO cho kíp đi làm (Ngày/Đêm) trùng Chủ Nhật hoặc Thứ Bảy HO.
     */
    fun getHOStatsForYear(crewId: String, year: Int, today: LocalDate): HOStats {
        if (crewId == "HC") return HOStats(0, 0)
        
        var total = 0
        var remaining = 0
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)
        
        var curr = startDate
        while (!curr.isAfter(endDate)) {
            val isOfficialHol = isHoliday(curr)
            val actualShift = getActualShift(crewId, curr)
            
            val isSunday = curr.dayOfWeek.value == 7
            val isSatWorkHO = isSaturdayHO(curr)

            val isHOReal = if (isSunday || isSatWorkHO) {
                if (isOfficialHol) {
                    getShift(crewId, curr) == ShiftType.NGAY
                } else {
                    actualShift != ShiftType.NGHI
                }
            } else {
                false
            }

            if (isHOReal) {
                total++
                if (!curr.isBefore(today)) {
                    remaining++
                }
            }
            curr = curr.plusDays(1)
        }
        return HOStats(total, remaining)
    }

    /**
     * Lấy tổng số ngày lễ chính thức trong năm
     */
    fun getHolidayCountForYear(year: Int): Int {
        var count = 0
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)
        var curr = startDate
        while (!curr.isAfter(endDate)) {
            if (isHoliday(curr)) {
                count++
            }
            curr = curr.plusDays(1)
        }
        return count
    }

    /**
     * Get the shift type for a specific crew on a specific date.
     */
    fun getShift(crewId: String, date: LocalDate): ShiftType {
        // HC = Hành Chính: Mon-Fri = Day, Sat-Sun = Off
        if (crewId == "HC") {
            return if (date.dayOfWeek.value <= 5) ShiftType.NGAY else ShiftType.NGHI
        }

        val crew = CREWS.find { it.id == crewId } ?: return ShiftType.NGHI
        val targetJDN = toJulianDayNumber(date)
        val diff = targetJDN - ANCHOR_JDN
        val holidayAdj = countHolidaysBetween(ANCHOR_DATE, date)
        val idx = mod(crew.offset + diff - holidayAdj, CYCLE.size)
        return CYCLE[idx]
    }

    // Bảng ca trực lễ năm 2026 thực tế từ ảnh
    private val REAL_2026_HOLIDAYS = mapOf(
        Pair(1, 1) to mapOf("A" to ShiftType.DEM, "B" to ShiftType.NGHI, "C" to ShiftType.NGAY),
        Pair(4, 26) to mapOf("A" to ShiftType.NGAY, "B" to ShiftType.NGHI, "C" to ShiftType.DEM),
        Pair(4, 30) to mapOf("A" to ShiftType.DEM, "B" to ShiftType.NGAY, "C" to ShiftType.NGHI),
        Pair(5, 1) to mapOf("A" to ShiftType.NGHI, "B" to ShiftType.NGAY, "C" to ShiftType.DEM),
        Pair(9, 1) to mapOf("A" to ShiftType.NGAY, "B" to ShiftType.DEM, "C" to ShiftType.NGHI),
        Pair(9, 2) to mapOf("A" to ShiftType.NGAY, "B" to ShiftType.NGHI, "C" to ShiftType.DEM),
        Pair(11, 24) to mapOf("A" to ShiftType.NGHI, "B" to ShiftType.NGAY, "C" to ShiftType.DEM),
        Pair(12, 31) to mapOf("A" to ShiftType.NGAY, "B" to ShiftType.DEM, "C" to ShiftType.NGHI)
    )

    /**
     * Get the actual shift type for a specific crew on a specific date,
     * taking official holidays into account.
     * On official holidays:
     * - HC is off.
     * - Only crews with actual work shifts (Day or Night) work, while other rests.
     */
    fun getActualShift(crewId: String, date: LocalDate): ShiftType {
        if (crewId == "HC") {
            return if (isHoliday(date) || date.dayOfWeek.value > 5) ShiftType.NGHI else ShiftType.NGAY
        }

        if (isLunarNewYear(date)) return ShiftType.NGHI

        if (isHoliday(date)) {
            val y = date.year
            val m = date.monthValue
            val d = date.dayOfMonth

            // 1. Nếu là năm 2026, sử dụng bảng ca trực lễ thực tế từ ảnh
            if (y == 2026) {
                val dayMonth = Pair(m, d)
                val holidayMap = REAL_2026_HOLIDAYS[dayMonth]
                if (holidayMap != null) {
                    return holidayMap[crewId] ?: ShiftType.NGHI
                }
            }

            // 2. Thuật toán tổng quát cho các năm khác
            val prevDay = date.minusDays(1)
            val nextDay = date.plusDays(1)

            val isPrevHol = isHoliday(prevDay) && !isLunarNewYear(prevDay)
            val isNextHol = isHoliday(nextDay) && !isLunarNewYear(nextDay)

            return when {
                isNextHol -> {
                    // Đây là ngày 1 của cụm lễ kép 2 ngày
                    val baseShifts = mapOf(
                        "A" to getShift("A", date),
                        "B" to getShift("B", date),
                        "C" to getShift("C", date)
                    )
                    val dayCrew = baseShifts.entries.first { it.value == ShiftType.NGAY }.key
                    val nightCrew = baseShifts.entries.first { it.value == ShiftType.DEM }.key
                    val offCrew = baseShifts.entries.first { it.value == ShiftType.NGHI }.key

                    when (crewId) {
                        offCrew -> ShiftType.NGAY
                        dayCrew -> ShiftType.DEM
                        nightCrew -> ShiftType.NGHI
                        else -> ShiftType.NGHI
                    }
                }
                isPrevHol -> {
                    // Đây là ngày 2 của cụm lễ kép 2 ngày
                    val baseShiftsPrev = mapOf(
                        "A" to getShift("A", prevDay),
                        "B" to getShift("B", prevDay),
                        "C" to getShift("C", prevDay)
                    )
                    val dayCrew = baseShiftsPrev.entries.first { it.value == ShiftType.NGAY }.key
                    val nightCrew = baseShiftsPrev.entries.first { it.value == ShiftType.DEM }.key
                    val offCrew = baseShiftsPrev.entries.first { it.value == ShiftType.NGHI }.key

                    when (crewId) {
                        offCrew -> ShiftType.NGAY
                        dayCrew -> ShiftType.NGHI
                        nightCrew -> ShiftType.DEM
                        else -> ShiftType.NGHI
                    }
                }
                else -> {
                    // Lễ đơn lẻ
                    val baseShifts = mapOf(
                        "A" to getShift("A", date),
                        "B" to getShift("B", date),
                        "C" to getShift("C", date)
                    )
                    val dayCrew = baseShifts.entries.first { it.value == ShiftType.NGAY }.key
                    val nightCrew = baseShifts.entries.first { it.value == ShiftType.DEM }.key
                    val offCrew = baseShifts.entries.first { it.value == ShiftType.NGHI }.key

                    when (crewId) {
                        dayCrew -> ShiftType.NGAY
                        nightCrew -> ShiftType.NGHI
                        offCrew -> ShiftType.DEM
                        else -> ShiftType.NGHI
                    }
                }
            }
        }
        return getShift(crewId, date)
    }

    /**
     * Get detailed shift info including holiday status and ca lam.
     * Cứ đi làm vào Chủ Nhật thì là ngày HO.
     * Đi làm vào Thứ Bảy cách tuần (bắt đầu từ thứ Bảy 3/1/2026) cũng là ngày HO.
     * Các ngày khác đi làm chỉ là HO nếu trùng vào ngày lễ chính thức.
     */
    fun getShiftInfo(crewId: String, date: LocalDate): ShiftInfo {
        val actualShift = getActualShift(crewId, date)
        val isOfficialHol = isHoliday(date)
        val holName = getHolidayName(date)

        // Đi làm vào Chủ Nhật (dayOfWeek.value == 7)
        val isSundayWork = (date.dayOfWeek.value == 7) && (actualShift != ShiftType.NGHI)

        // Đi làm vào Thứ Bảy HO (theo 3 giai đoạn: trước 7/2026 mọi T7, 7-12/2026 T7 cách tuần, từ 2027 không)
        val isSaturdayWork = isSaturdayHO(date) && (actualShift != ShiftType.NGHI)

        // Ngày được coi là ngày HO hoặc lễ nếu: trùng lễ chính thức, hoặc đi làm Chủ Nhật, hoặc đi làm Thứ Bảy cách tuần
        val isHOorHoliday = isOfficialHol || isSundayWork || isSaturdayWork

        val finalHolName = when {
            isOfficialHol -> holName
            isSundayWork -> "Chủ Nhật đi làm (HO)"
            isSaturdayWork -> "Thứ Bảy đi làm (HO)"
            else -> null
        }

        return ShiftInfo(actualShift, isHOorHoliday, finalHolName)
    }

    /**
     * Check if a date is a public holiday.
     * Hỗ trợ động mọi năm cho Tết Dương Lịch, Tết Nguyên Đán, Giỗ Tổ Hùng Vương,
     * 30/4, 1/5, Quốc Khánh (1/9, 2/9), Ngày thành lập (24/11), và ngày cuối năm SDV Day (31/12).
     */
    fun isHoliday(date: LocalDate): Boolean {
        val y = date.year
        val m = date.monthValue
        val d = date.dayOfMonth

        // 1. Các ngày lễ dương lịch cố định hàng năm
        if (m == 1 && d == 1) return true   // Tết Dương lịch
        if (m == 4 && d == 30) return true  // Giải phóng miền Nam
        if (m == 5 && d == 1) return true   // Quốc tế Lao động
        if (m == 9 && (d == 1 || d == 2)) return true // Quốc khánh
        if (m == 11 && d == 24) return true // Kỷ niệm ngày thành lập công ty (hiển thị nhãn Lễ)
        if (m == 12 && d == 31) return true // SDV Day (ngày cuối của mọi năm, hiển thị nhãn SDV)

        // 2. Các ngày lễ âm lịch theo năm (Tết Nguyên Đán và Giỗ Tổ Hùng Vương)
        return when (y) {
            2025 -> {
                (m == 1 && d in 28..31) || (m == 2 && d == 1) || (m == 4 && d == 7)
            }
            2026 -> {
                (m == 2 && d in 16..20) || (m == 4 && d == 26)
            }
            2027 -> {
                (m == 2 && d in 5..9) || (m == 4 && d == 16)
            }
            2028 -> {
                (m == 1 && d in 25..29) || (m == 4 && d == 4)
            }
            2029 -> {
                (m == 2 && d in 12..16) || (m == 4 && d == 23)
            }
            else -> false
        }
    }

    /**
     * Kiểm tra xem ngày có thuộc 5 ngày nghỉ Tết Âm lịch hàng năm hay không.
     */
    fun isLunarNewYear(date: LocalDate): Boolean {
        val y = date.year
        val m = date.monthValue
        val d = date.dayOfMonth
        return when (y) {
            2025 -> (m == 1 && d in 28..31) || (m == 2 && d == 1)
            2026 -> m == 2 && d in 16..20
            2027 -> m == 2 && d in 5..9
            2028 -> m == 1 && d in 25..29
            2029 -> m == 2 && d in 12..16
            else -> false
        }
    }

    /**
     * Get holiday name if the date is a holiday.
     */
    fun getHolidayName(date: LocalDate): String? {
        val y = date.year
        val m = date.monthValue
        val d = date.dayOfMonth

        return when {
            m == 1 && d == 1 -> "Tết Dương lịch"
            m == 4 && d == 30 -> "Giải phóng miền Nam"
            m == 5 && d == 1 -> "Quốc tế Lao động"
            m == 9 && (d == 1 || d == 2) -> "Quốc khánh"
            m == 11 && d == 24 -> "Kỷ niệm ngày thành lập" // Bỏ chữ SDV để hiển thị nhãn "Lễ"
            m == 12 && d == 31 -> "SDV Day"
            // Âm lịch
            y == 2025 && ((m == 1 && d in 28..31) || (m == 2 && d == 1)) -> "Tết Nguyên Đán"
            y == 2025 && m == 4 && d == 7 -> "Giỗ Tổ Hùng Vương"
            
            y == 2026 && (m == 2 && d in 16..20) -> "Tết Nguyên Đán"
            y == 2026 && m == 4 && d == 26 -> "Giỗ Tổ Hùng Vương"
            
            y == 2027 && (m == 2 && d in 5..9) -> "Tết Nguyên Đán"
            y == 2027 && m == 4 && d == 16 -> "Giỗ Tổ Hùng Vương"
            
            y == 2028 && (m == 1 && d in 25..29) -> "Tết Nguyên Đán"
            y == 2028 && m == 4 && d == 4 -> "Giỗ Tổ Hùng Vương"
            
            y == 2029 && (m == 2 && d in 12..16) -> "Tết Nguyên Đán"
            y == 2029 && m == 4 && d == 23 -> "Giỗ Tổ Hùng Vương"
            
            isHoliday(date) -> "Ngày lễ"
            else -> null
        }
    }

    /**
     * Count holidays strictly between two LocalDates using simple loops (extremely safe).
     */
    private fun countHolidaysBetween(anchorDate: LocalDate, targetDate: LocalDate): Int {
        var count = 0
        if (targetDate.isAfter(anchorDate)) {
            var curr = anchorDate.plusDays(1)
            while (curr.isBefore(targetDate)) {
                if (isHoliday(curr)) {
                    count++
                }
                curr = curr.plusDays(1)
            }
        } else if (targetDate.isBefore(anchorDate)) {
            var curr = targetDate.plusDays(1)
            while (curr.isBefore(anchorDate) || curr == anchorDate) {
                if (isHoliday(curr)) {
                    count--
                }
                curr = curr.plusDays(1)
            }
        }
        return count
    }

    /**
     * Convert a LocalDate to Julian Day Number.
     */
    private fun toJulianDayNumber(date: LocalDate): Int {
        val y = date.year
        val m = date.monthValue
        val d = date.dayOfMonth
        val a = (14 - m) / 12
        val yy = y + 4800 - a
        val mm = m + 12 * a - 3
        return d + (153 * mm + 2) / 5 + 365 * yy + yy / 4 - yy / 100 + yy / 400 - 32045
    }

    private fun mod(a: Int, b: Int): Int {
        return ((a % b) + b) % b
    }

    /**
     * Find the next working day (NGAY or DEM) for a crew starting from a given date.
     */
    fun findNextWorkDay(crewId: String, fromDate: LocalDate): Pair<LocalDate, ShiftType> {
        var date = fromDate
        for (i in 0..30) { // Look ahead up to 30 days
            val shift = getShift(crewId, date)
            if (shift != ShiftType.NGHI) {
                return Pair(date, shift)
            }
            date = date.plusDays(1)
        }
        return Pair(fromDate, ShiftType.NGHI)
    }
}
