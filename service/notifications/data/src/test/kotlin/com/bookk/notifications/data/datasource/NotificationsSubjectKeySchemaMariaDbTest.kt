package com.bookk.notifications.data.datasource

import library.encryption.impl.test.SubjectKeySchemaContract

internal class NotificationsSubjectKeySchemaMariaDbTest : SubjectKeySchemaContract(
    serviceSchema = "notifications",
    keySchema = "notifications_keys",
)
