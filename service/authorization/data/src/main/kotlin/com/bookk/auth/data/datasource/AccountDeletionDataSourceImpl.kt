package com.bookk.auth.data.datasource

import com.bookk.auth.data.orm.table.AccountDeletionTable
import com.bookk.auth.data.orm.table.AuthenticationTable
import com.bookk.auth.domain.api.delete_account.entity.DeletionReminder
import com.bookk.auth.domain.api.delete_account.entity.PendingAccountDeletion
import com.bookk.auth.domain.datasource.AccountDeletionDataSource
import com.bookk.core.data.DataSource
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AccountDeletionDataSourceImpl : DataSource(), AccountDeletionDataSource {

    override suspend fun schedule(authId: Uuid, requestedAt: Instant, deletesAt: Instant): Unit = dbQuery {
        AccountDeletionTable.insertIgnore {
            it[this.authId] = authId
            it[this.requestedAt] = requestedAt
            it[this.deletesAt] = deletesAt
        }
    }

    override suspend fun get(authId: Uuid): PendingAccountDeletion? = dbQuery {
        selectPending { AccountDeletionTable.authId eq authId }.singleOrNull()
    }

    override suspend fun getByUserId(userId: Uuid): PendingAccountDeletion? = dbQuery {
        selectPending { AuthenticationTable.userId eq userId }.singleOrNull()
    }

    override suspend fun getDueBy(cutoff: Instant): List<PendingAccountDeletion> = dbQuery {
        selectPending { (AccountDeletionTable.deletesAt lessEq cutoff) and (AuthenticationTable.deletionHold eq false) }
    }

    override suspend fun setReminder(authId: Uuid, reminder: DeletionReminder): Unit = dbQuery {
        AccountDeletionTable.update(where = { AccountDeletionTable.authId eq authId }) {
            it[this.reminder] = reminder
        }
    }

    override suspend fun cancel(authId: Uuid): Boolean = dbQuery {
        AccountDeletionTable.deleteWhere { AccountDeletionTable.authId eq authId } > 0
    }

    private fun selectPending(where: () -> Op<Boolean>): List<PendingAccountDeletion> {
        return AccountDeletionTable.innerJoin(AuthenticationTable)
            .select(
                AccountDeletionTable.authId,
                AuthenticationTable.userId,
                AccountDeletionTable.deletesAt,
                AccountDeletionTable.reminder,
                AuthenticationTable.deletionHold,
            )
            .where(where())
            .map { it.toPendingAccountDeletion() }
    }

    private fun ResultRow.toPendingAccountDeletion(): PendingAccountDeletion {
        return PendingAccountDeletion(
            authId = this[AccountDeletionTable.authId].value,
            userId = this[AuthenticationTable.userId],
            deletesAt = this[AccountDeletionTable.deletesAt],
            reminder = this[AccountDeletionTable.reminder],
            deletionHold = this[AuthenticationTable.deletionHold],
        )
    }
}
