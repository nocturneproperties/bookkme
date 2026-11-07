package com.bookk.auth.data.datasource

import com.bookk.auth.data.orm.table.ErasedAccountTable
import com.bookk.auth.domain.datasource.ErasedAccountDataSource
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

internal class ErasedAccountDataSourceImpl : DataSource(), ErasedAccountDataSource {

    override suspend fun record(userId: Uuid) = dbQuery<Unit> {
        ErasedAccountTable.insertIgnore {
            it[this.userId] = userId
            it[erasedAt] = Clock.System.now()
        }
    }

    override suspend fun getReapplyRequested(): List<Uuid> = dbQuery {
        ErasedAccountTable.select(ErasedAccountTable.userId)
            .where { ErasedAccountTable.reapplyRequested eq true }
            .map { it[ErasedAccountTable.userId] }
    }

    override suspend fun claimReapply(userId: Uuid): Boolean = dbQuery {
        ErasedAccountTable.update(
            where = { (ErasedAccountTable.userId eq userId) and (ErasedAccountTable.reapplyRequested eq true) },
        ) {
            it[reapplyRequested] = false
        } > 0
    }

    override suspend fun deleteRecordedBefore(cutoff: Instant) = dbQuery<Unit> {
        ErasedAccountTable.deleteWhere { ErasedAccountTable.erasedAt less cutoff }
    }
}
