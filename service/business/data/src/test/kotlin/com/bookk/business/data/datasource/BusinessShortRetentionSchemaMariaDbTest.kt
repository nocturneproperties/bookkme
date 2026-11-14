package com.bookk.business.data.datasource

import com.bookk.business.domain.api.BUSINESS_SCHEMA
import com.bookk.business.domain.api.BUSINESS_SHORT_RETENTION_SCHEMA
import library.encryption.impl.test.ShortRetentionSchemaContract

internal class BusinessShortRetentionSchemaMariaDbTest : ShortRetentionSchemaContract(
    serviceSchema = BUSINESS_SCHEMA,
    shortRetentionSchema = BUSINESS_SHORT_RETENTION_SCHEMA,
    serviceTables = setOf("outbox_event", "exhausted_event", "data_export"),
)
