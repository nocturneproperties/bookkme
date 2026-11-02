package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.table.DeletedClientTable
import com.bookk.appointments.domain.datasource.DeletedClientDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.data.forShare
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class DeletedClientDataSourceImpl : DataSource(), DeletedClientDataSource {

    override suspend fun markDeleted(businessId: Uuid, clientId: Uuid) = dbQuery<Unit> {
        DeletedClientTable.insertIgnore {
            it[this.clientId] = clientId
            it[this.businessId] = businessId
            it[deletedAt] = Clock.System.now()
        }
    }

    override suspend fun isDeletedForShare(businessId: Uuid, clientId: Uuid): Boolean = dbQuery {
        DeletedClientTable
            .select(DeletedClientTable.clientId)
            .where { (DeletedClientTable.clientId eq clientId) and (DeletedClientTable.businessId eq businessId) }
            .forShare()
            .any()
    }
}
