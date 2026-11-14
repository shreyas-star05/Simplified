package com.dusk.core.journal

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Always 6 rows of 7, so months don't change height as you swipe. Days outside the month are null. */
fun monthCells(month: YearMonth, weekStart: DayOfWeek = DayOfWeek.MONDAY): List<LocalDate?> {
    val first = month.atDay(1)
    val lead = (first.dayOfWeek.value - weekStart.value + 7) % 7
    return List(42) { i ->
        val day = i - lead + 1
        if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
    }
}

fun weekdayInitials(weekStart: DayOfWeek = DayOfWeek.MONDAY): List<String> {
    val names = listOf("M", "T", "W", "T", "F", "S", "S")
    return List(7) { names[(weekStart.value - 1 + it) % 7] }
}
