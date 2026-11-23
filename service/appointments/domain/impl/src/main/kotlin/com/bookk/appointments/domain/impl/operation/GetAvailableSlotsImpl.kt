package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.BookedRange
import com.bookk.appointments.domain.api.entity.DaySlots
import com.bookk.appointments.domain.api.entity.RequestedService
import com.bookk.appointments.domain.api.entity.TimeSlot
import com.bookk.appointments.domain.api.operation.GetAvailableSlots
import com.bookk.appointments.domain.api.operation.GetAvailableSlots.Companion.MAX_DAYS
import com.bookk.appointments.domain.api.operation.GetAvailableSlots.Companion.MAX_SERVICES
import com.bookk.appointments.domain.api.operation.GetAvailableSlots.Companion.STEP_MINUTES
import com.bookk.appointments.domain.api.operation.GetAvailableSlots.Error
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.business.domain.api.appointment.operation.GetAppointmentRescheduleContext
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.business.client.api.BusinessClient
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import library.schedule.Schedule
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid
import com.bookk.core.domain.entity.Error as CoreError

internal class GetAvailableSlotsImpl(
    private val settingsDataSource: AppointmentSettingsDataSource,
    private val subscriptionDataSource: AppointmentSubscriptionDataSource,
    appointmentDataSource: AppointmentDataSource,
    private val businessClient: BusinessClient,
    private val transactionManager: TransactionManager,
    private val clock: Clock,
) : GetAvailableSlots {

    private val employeeBookings = EmployeeBookings(appointmentDataSource)

    override suspend fun invoke(
        businessId: Uuid,
        employeeId: Uuid,
        services: List<RequestedService>,
        from: LocalDate,
        to: LocalDate,
    ): Result<List<DaySlots>> {
        if (!services.isValidSelection()) return Result.failure(Error.InvalidServiceSelection())
        if (!isValidRange(from, to)) return Result.failure(Error.InvalidRange())
        return runSuspendCatching {
            val settings = transactionManager.transactionOrThrow {
                if (!subscriptionDataSource.isBusinessEnabled(businessId)) throw CoreError.NotFound()
                settingsDataSource.get(businessId) ?: throw CoreError.NotFound()
            }
            val context = businessClient
                .getAppointmentRescheduleContext(businessId, employeeId, services.map { it.serviceId }, false)
                .getOrThrow()
            val employeeSchedule = requireNotNull(context.employee).schedule
            val duration = totalDuration(services, context.services)
            val days = datesFrom(from, to)
            val window = windowOf(days, settings, duration)
            val bookings = transactionManager.transactionOrThrow {
                employeeBookings.overlapping(businessId, employeeId, window)
            }
            val finder = SlotFinder(settings, employeeSchedule, duration, bookings, clock.now())
            days.map { DaySlots(date = it, slots = finder.slotsOn(it)) }
        }
    }

    private fun List<RequestedService>.isValidSelection(): Boolean {
        val serviceIds = map { it.serviceId }
        val hasPositiveCounts = all { it.count > 0 }
        val isDistinct = serviceIds.distinct().size == serviceIds.size
        return isNotEmpty() && hasPositiveCounts && isDistinct && sumOf { it.count.toLong() } <= MAX_SERVICES
    }

    private fun isValidRange(from: LocalDate, to: LocalDate): Boolean {
        val days = from.daysUntil(to) + 1
        val isInCalendar = from.year >= MIN_YEAR && to.year <= MAX_YEAR
        return isInCalendar && days in 1..MAX_DAYS
    }

    private fun totalDuration(requested: List<RequestedService>, services: List<Service>): Duration {
        val durations = services.associate { it.id to it.duration }
        return requested.fold(Duration.ZERO) { total, service ->
            val duration = durations[service.serviceId] ?: throw GetAppointmentRescheduleContext.Error.ServiceNotFound()
            total + duration * service.count
        }
    }

    private fun datesFrom(from: LocalDate, to: LocalDate): List<LocalDate> {
        return generateSequence(from) { it.nextDay() }.takeWhile { it <= to }.toList()
    }

    private fun windowOf(
        days: List<LocalDate>,
        settings: AppointmentSettings,
        duration: Duration,
    ): OpenEndRange<Instant> {
        val gap = settings.bookingGap()
        val start = days.first().atStartOfDayIn(settings.timeZone)
        val end = days.last().nextDay().atStartOfDayIn(settings.timeZone)
        return start - gap..<end + duration + gap
    }
}

private class SlotFinder(
    private val settings: AppointmentSettings,
    private val employeeSchedule: Schedule,
    private val duration: Duration,
    private val bookings: List<BookedRange>,
    private val now: Instant,
) {
    private val gap = settings.bookingGap()
    private val step = STEP_MINUTES.minutes

    fun slotsOn(date: LocalDate): List<TimeSlot> {
        val dayEnd = date.nextDay().atStartOfDayIn(settings.timeZone)
        return generateSequence(date.atStartOfDayIn(settings.timeZone)) { it + step }
            .takeWhile { it < dayEnd }
            .filter(::isFree)
            .map { TimeSlot(start = it, end = it + duration) }
            .toList()
    }

    private fun isFree(start: Instant): Boolean {
        val end = start + duration
        if (start < now) return false
        if (!settings.isBookable(start, end, employeeSchedule)) return false
        return bookings.none { it.blocks(start, end, gap) }
    }
}

private const val MIN_YEAR = 1970
private const val MAX_YEAR = 9999

private fun LocalDate.nextDay(): LocalDate {
    return plus(DatePeriod(days = 1))
}
