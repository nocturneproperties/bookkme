package com.bookk.user.data.datasource

import library.encryption.impl.test.SubjectKeySchemaContract

internal class UserSubjectKeySchemaMariaDbTest : SubjectKeySchemaContract(
    serviceSchema = "user",
    keySchema = "user_keys",
)
