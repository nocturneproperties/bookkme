package com.bookk.appointments.domain.api.entity

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

data class HistoryDates(val from: LocalDate? = null, val to: LocalDate? = null) {
    val isValid: Boolean
        get() = from == null || to == null || from <= to

    val isOpen: Boolean
        get() = from == null && to == null

    fun startBounds(timeZone: TimeZone): StartBounds {
        return StartBounds(
            from = from?.atStartOfDayIn(timeZone),
            until = to?.plus(DatePeriod(days = 1))?.atStartOfDayIn(timeZone),
        )
    }
}
