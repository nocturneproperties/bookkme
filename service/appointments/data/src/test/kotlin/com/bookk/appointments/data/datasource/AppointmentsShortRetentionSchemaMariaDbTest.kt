package com.bookk.appointments.data.datasource

import com.bookk.appointments.domain.api.APPOINTMENTS_SCHEMA
import com.bookk.appointments.domain.api.APPOINTMENTS_SHORT_RETENTION_SCHEMA
import library.encryption.impl.test.ShortRetentionSchemaContract

internal class AppointmentsShortRetentionSchemaMariaDbTest : ShortRetentionSchemaContract(
    serviceSchema = APPOINTMENTS_SCHEMA,
    shortRetentionSchema = APPOINTMENTS_SHORT_RETENTION_SCHEMA,
    serviceTables = setOf("outbox_event", "exhausted_event"),
)
