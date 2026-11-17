package com.bookk.business.data.datasource

import com.bookk.business.data.orm.table.UserLimitsTable
import com.bookk.business.domain.api.user.entity.UserLimits
import com.bookk.business.domain.datasource.UserLimitsDataSource
import com.bookk.core.data.DataSource
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

internal class UserLimitsDataSourceImpl : DataSource(), UserLimitsDataSource {
    override suspend fun lockForUser(userId: Uuid): UserLimits = dbQuery {
        UserLimitsTable.insertIgnore { it[UserLimitsTable.userId] = userId }
        UserLimitsTable.selectAll()
            .where { UserLimitsTable.userId eq userId }
            .forUpdate()
            .single()
            .toUserLimits()
    }

    override suspend fun deleteForUser(userId: Uuid) = dbQuery<Unit> {
        UserLimitsTable.deleteWhere { UserLimitsTable.userId eq userId }
    }

    private fun ResultRow.toUserLimits(): UserLimits {
        return UserLimits(
            userId = this[UserLimitsTable.userId],
            maxBusinesses = this[UserLimitsTable.maxBusinesses] ?: UserLimits.DEFAULT_MAX_BUSINESSES,
        )
    }
}
