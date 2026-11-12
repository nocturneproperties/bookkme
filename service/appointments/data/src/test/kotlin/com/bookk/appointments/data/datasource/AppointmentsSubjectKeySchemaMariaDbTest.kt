package com.bookk.appointments.data.datasource

import library.encryption.impl.test.SubjectKeySchemaContract

internal class AppointmentsSubjectKeySchemaMariaDbTest : SubjectKeySchemaContract(
    serviceSchema = "appointments",
    keySchema = "appointments_keys",
)
