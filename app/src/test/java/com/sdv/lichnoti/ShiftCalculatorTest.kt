package com.sdv.lichnoti

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ShiftCalculatorTest {

    @Test
    fun lunarHolidaysContinueAfter2029() {
        // Lịch Việt Nam lệch lịch Trung Quốc một ngày trong năm 2030: mùng 1 là 02/02.
        val tetHoliday = (1..5).map { LocalDate.of(2030, 2, it) }
        tetHoliday.forEach { date ->
            assertTrue("Expected Tết holiday on $date", ShiftCalculator.isLunarNewYear(date))
            assertTrue("Expected official holiday on $date", ShiftCalculator.isHoliday(date))
        }

        assertFalse(ShiftCalculator.isLunarNewYear(LocalDate.of(2030, 1, 31)))
        assertFalse(ShiftCalculator.isLunarNewYear(LocalDate.of(2030, 2, 6)))
        assertTrue(ShiftCalculator.isHoliday(LocalDate.of(2030, 4, 12)))
        assertTrue(
            ShiftCalculator.getHolidayName(LocalDate.of(2030, 4, 12))
                ?.contains("Hùng Vương") == true
        )
    }

    @Test
    fun existing2025To2029TetDatesRemainCompatible() {
        listOf(
            LocalDate.of(2025, 1, 28),
            LocalDate.of(2026, 2, 16),
            LocalDate.of(2027, 2, 5),
            LocalDate.of(2028, 1, 25),
            LocalDate.of(2029, 2, 12)
        ).forEach { date -> assertTrue("Expected Tết holiday on $date", ShiftCalculator.isLunarNewYear(date)) }
    }

    @Test
    fun officialHolidayCountsHoOnlyForDayCrew() {
        val newYear2026 = LocalDate.of(2026, 1, 1)
        assertTrue(ShiftCalculator.isHODateForStats("C", newYear2026))
        assertFalse(ShiftCalculator.isHODateForStats("A", newYear2026))
        assertFalse(ShiftCalculator.isHODateForStats("B", newYear2026))
    }

    @Test
    fun cachedHolidayAdjustmentMatchesLegacyDayByDayCalculation() {
        val offsets = mapOf("A" to 0, "B" to 8, "C" to 4)
        var date = LocalDate.of(2025, 1, 1)
        val end = LocalDate.of(2031, 12, 31)
        while (!date.isAfter(end)) {
            offsets.forEach { (crew, offset) ->
                assertEquals(
                    "Shift mismatch for crew $crew on $date",
                    legacyShift(offset, date),
                    ShiftCalculator.getShift(crew, date)
                )
            }
            date = date.plusDays(17)
        }
    }

    private fun legacyShift(offset: Int, target: LocalDate): ShiftCalculator.ShiftType {
        val anchor = LocalDate.of(2026, 1, 3)
        val cycle = arrayOf(
            ShiftCalculator.ShiftType.NGAY,
            ShiftCalculator.ShiftType.NGAY,
            ShiftCalculator.ShiftType.NGAY,
            ShiftCalculator.ShiftType.NGAY,
            ShiftCalculator.ShiftType.NGHI,
            ShiftCalculator.ShiftType.NGHI,
            ShiftCalculator.ShiftType.DEM,
            ShiftCalculator.ShiftType.DEM,
            ShiftCalculator.ShiftType.DEM,
            ShiftCalculator.ShiftType.DEM,
            ShiftCalculator.ShiftType.NGHI,
            ShiftCalculator.ShiftType.NGHI
        )
        val diff = ChronoUnit.DAYS.between(anchor, target).toInt()
        var holidayAdjustment = 0
        if (target.isAfter(anchor)) {
            var cursor = anchor.plusDays(1)
            while (cursor.isBefore(target)) {
                if (ShiftCalculator.isHoliday(cursor)) holidayAdjustment++
                cursor = cursor.plusDays(1)
            }
        } else if (target.isBefore(anchor)) {
            var cursor = target.plusDays(1)
            while (!cursor.isAfter(anchor)) {
                if (ShiftCalculator.isHoliday(cursor)) holidayAdjustment--
                cursor = cursor.plusDays(1)
            }
        }
        return cycle[Math.floorMod(offset + diff - holidayAdjustment, cycle.size)]
    }
}
