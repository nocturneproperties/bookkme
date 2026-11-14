package com.bookk.user.data.datasource

import com.bookk.core.data.DataSource
import com.bookk.user.data.orm.entity.UserEntity
import com.bookk.user.domain.api.export.ProfileExport
import com.bookk.user.domain.datasource.UserDataExportDataSource
import kotlin.uuid.Uuid

internal class UserDataExportDataSourceImpl(
    private val profileCipher: ProfileCipher,
) : DataSource(), UserDataExportDataSource {

    override suspend fun getProfile(userId: Uuid): ProfileExport? = dbQuery {
        val entity = UserEntity.findById(userId) ?: return@dbQuery null
        val user = profileCipher.decrypt(entity)
        ProfileExport(
            id = user.id,
            name = user.name,
            lastName = user.lastName,
            email = user.email,
            phone = user.phone,
            createdAt = entity.createdAt,
        )
    }
}
