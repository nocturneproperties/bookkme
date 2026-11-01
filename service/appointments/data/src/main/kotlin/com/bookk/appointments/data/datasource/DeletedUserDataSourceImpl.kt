package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.table.DeletedUserTable
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.data.forShare
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class DeletedUserDataSourceImpl : DataSource(), DeletedUserDataSource {

    override suspend fun markDeleted(userId: Uuid) = dbQuery<Unit> {
        DeletedUserTable.insertIgnore {
            it[this.userId] = userId
            it[deletedAt] = Clock.System.now()
        }
    }

    override suspend fun isDeletedForShare(userId: Uuid): Boolean = dbQuery {
        DeletedUserTable
            .select(DeletedUserTable.userId)
            .where { DeletedUserTable.userId eq userId }
            .forShare()
            .any()
    }
}
