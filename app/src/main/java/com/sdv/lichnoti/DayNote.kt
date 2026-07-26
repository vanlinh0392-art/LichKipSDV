package com.sdv.lichnoti

import java.time.LocalDate

data class DayNote(
    val date: LocalDate,
    val html: String,
    val plainText: String,
    val updatedAt: Long
)
