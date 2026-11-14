package com.bookk.notifications.data.datasource

import com.bookk.notifications.domain.api.NOTIFICATIONS_SCHEMA
import com.bookk.notifications.domain.api.NOTIFICATIONS_SHORT_RETENTION_SCHEMA
import library.encryption.impl.test.ShortRetentionSchemaContract

internal class NotificationsShortRetentionSchemaMariaDbTest : ShortRetentionSchemaContract(
    serviceSchema = NOTIFICATIONS_SCHEMA,
    shortRetentionSchema = NOTIFICATIONS_SHORT_RETENTION_SCHEMA,
    serviceTables = setOf("exhausted_event"),
)
