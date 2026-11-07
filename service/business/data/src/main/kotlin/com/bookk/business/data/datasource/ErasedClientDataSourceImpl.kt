package com.bookk.business.data.datasource

import com.bookk.business.data.orm.table.ErasedClientTable
import com.bookk.business.domain.api.client.entity.ErasedClient
import com.bookk.business.domain.datasource.ErasedClientDataSource
import com.bookk.core.data.DataSource
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ErasedClientDataSourceImpl : DataSource(), ErasedClientDataSource {

    override suspend fun record(businessId: Uuid, clientId: Uuid) = dbQuery<Unit> {
        ErasedClientTable.insertIgnore {
            it[this.clientId] = clientId
            it[this.businessId] = businessId
            it[erasedAt] = Clock.System.now()
        }
    }

    override suspend fun getReapplyRequested(): List<ErasedClient> = dbQuery {
        ErasedClientTable.select(ErasedClientTable.businessId, ErasedClientTable.clientId)
            .where { ErasedClientTable.reapplyRequested eq true }
            .map { ErasedClient(businessId = it[ErasedClientTable.businessId], clientId = it[ErasedClientTable.clientId]) }
    }

    override suspend fun claimReapply(clientId: Uuid): Boolean = dbQuery {
        ErasedClientTable.update(
            where = { (ErasedClientTable.clientId eq clientId) and (ErasedClientTable.reapplyRequested eq true) },
        ) {
            it[reapplyRequested] = false
        } > 0
    }

    override suspend fun deleteRecordedBefore(cutoff: Instant) = dbQuery<Unit> {
        ErasedClientTable.deleteWhere { ErasedClientTable.erasedAt less cutoff }
    }
}
