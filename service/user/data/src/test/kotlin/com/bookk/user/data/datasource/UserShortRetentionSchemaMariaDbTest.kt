package com.bookk.user.data.datasource

import com.bookk.user.domain.api.USER_SCHEMA
import com.bookk.user.domain.api.USER_SHORT_RETENTION_SCHEMA
import library.encryption.impl.test.ShortRetentionSchemaContract

internal class UserShortRetentionSchemaMariaDbTest : ShortRetentionSchemaContract(
    serviceSchema = USER_SCHEMA,
    shortRetentionSchema = USER_SHORT_RETENTION_SCHEMA,
    serviceTables = setOf("outbox_event", "exhausted_event", "data_export"),
)
