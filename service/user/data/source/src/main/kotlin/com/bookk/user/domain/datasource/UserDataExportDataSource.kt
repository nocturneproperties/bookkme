package com.bookk.user.domain.datasource

import com.bookk.user.domain.api.export.ContactMessageExport
import com.bookk.user.domain.api.export.ProfileExport
import kotlin.uuid.Uuid

interface UserDataExportDataSource {
    suspend fun getProfile(userId: Uuid): ProfileExport?
    suspend fun getContactMessages(userId: Uuid): List<ContactMessageExport>
}
