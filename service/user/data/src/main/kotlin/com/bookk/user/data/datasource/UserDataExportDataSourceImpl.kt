package com.bookk.user.data.datasource

import com.bookk.core.data.DataSource
import com.bookk.user.data.orm.table.ContactFormTable
import com.bookk.user.data.orm.table.UserTable
import com.bookk.user.domain.api.entity.ContactForm
import com.bookk.user.domain.api.export.ContactMessageExport
import com.bookk.user.domain.api.export.ProfileExport
import com.bookk.user.domain.datasource.UserDataExportDataSource
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

internal class UserDataExportDataSourceImpl : DataSource(), UserDataExportDataSource {

    override suspend fun getProfile(userId: Uuid): ProfileExport? = dbQuery {
        UserTable.selectAll().where { UserTable.id eq userId }.singleOrNull()?.let {
            ProfileExport(
                id = it[UserTable.id].value,
                name = it[UserTable.name],
                lastName = it[UserTable.lastName],
                email = it[UserTable.email],
                phone = it[UserTable.phone],
                createdAt = it[UserTable.createdAt],
            )
        }
    }

    override suspend fun getContactMessages(userId: Uuid): List<ContactMessageExport> = dbQuery {
        ContactFormTable.selectAll()
            .where { ContactFormTable.userId eq userId }
            .orderBy(ContactFormTable.createdAt, SortOrder.ASC)
            .map {
                ContactMessageExport(
                    text = it[ContactFormTable.text],
                    usageLogs = it[ContactFormTable.usageLogs],
                    status = ContactForm.ContactFormStatus.entries.firstOrNull { status -> status.id == it[ContactFormTable.status] }?.name ?: it[ContactFormTable.status].toString(),
                    sentAt = it[ContactFormTable.createdAt],
                )
            }
    }
}
