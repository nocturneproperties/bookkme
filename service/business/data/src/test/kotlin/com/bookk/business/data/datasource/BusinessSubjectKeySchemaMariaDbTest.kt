package com.bookk.business.data.datasource

import library.encryption.impl.test.SubjectKeySchemaContract

internal class BusinessSubjectKeySchemaMariaDbTest : SubjectKeySchemaContract(
    serviceSchema = "business",
    keySchema = "business_keys",
)
