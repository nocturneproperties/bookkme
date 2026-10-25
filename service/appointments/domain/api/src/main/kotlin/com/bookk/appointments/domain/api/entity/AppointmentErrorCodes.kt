package com.bookk.appointments.domain.api.entity

object AppointmentErrorCodes {
    private const val BASE = 300000

    const val REQUEST_EXISTS = BASE + 1
    const val TIME_NOT_ALLOWED = BASE + 2
    const val DATE_NOT_ALLOWED = BASE + 3
    const val APPOINTMENT_EXISTS = BASE + 4
    const val APPOINTMENT_ALREADY_CANCELED = BASE + 5
    const val APPOINTMENT_ALREADY_COMPLETED = BASE + 6
    const val REQUEST_ALREADY_DECLINED = BASE + 7
    const val REQUEST_ALREADY_APPROVED = BASE + 8
    const val PLUGIN_ALREADY_ENABLED = BASE + 9
    const val DATE_IN_PAST = BASE + 12
    const val PRICE_CHANGED = BASE + 13
    const val SERVICES_VALIDATION_FAILED = BASE + 14
    const val INSTANT_APPOINTMENT_TO_OTHER = BASE + 15
    const val QUOTE_TOKEN_ALREADY_USED = BASE + 16
    const val DURATION_CHANGED = BASE + 17
    const val APPOINTMENT_NOT_STARTED = BASE + 18
    const val APPOINTMENT_MARKED_NO_SHOW = BASE + 19
    const val PRICE_ADJUSTMENT_NEGATIVE_PRICE = BASE + 21
    const val PRICE_ADJUSTMENT_CURRENCY_MISMATCH = BASE + 22
    const val PRICE_ADJUSTMENT_REASON_TOO_LONG = BASE + 23
    const val SERVICE_SELECTION_INVALID = BASE + 24
}