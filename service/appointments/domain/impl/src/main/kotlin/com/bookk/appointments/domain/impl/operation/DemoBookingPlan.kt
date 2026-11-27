package com.bookk.appointments.domain.impl.operation

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal object DemoBookingPlan {
    private const val DAYS_AROUND_TODAY = 3
    private const val REQUEST_DAYS_AHEAD = 7
    private val appointmentTimes = listOf(LocalTime(10, 0), LocalTime(13, 0), LocalTime(15, 30))
    private val requestTime = LocalTime(11, 30)

    fun appointmentStarts(now: Instant, timeZone: TimeZone): List<Instant> {
        val today = now.toLocalDateTime(timeZone).date
        return (-DAYS_AROUND_TODAY..DAYS_AROUND_TODAY).flatMap { offset ->
            val date = today.plus(offset, DateTimeUnit.DAY)
            appointmentTimes.map { date.at(it, timeZone) }
        }
    }

    fun requestStarts(now: Instant, timeZone: TimeZone): List<Instant> {
        val today = now.toLocalDateTime(timeZone).date
        return (1..REQUEST_DAYS_AHEAD).map { today.plus(it, DateTimeUnit.DAY).at(requestTime, timeZone) }
    }

    private fun LocalDate.at(time: LocalTime, timeZone: TimeZone): Instant {
        return LocalDateTime(this, time).toInstant(timeZone)
    }
}
