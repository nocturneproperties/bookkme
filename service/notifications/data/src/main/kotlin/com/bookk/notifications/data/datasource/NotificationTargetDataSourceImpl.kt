package com.bookk.notifications.data.datasource

import com.bookk.core.data.DataSource
import com.bookk.notifications.data.orm.table.NotificationEmailTargetTable
import com.bookk.notifications.data.orm.table.NotificationTelegramTargetTable
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class NotificationTargetDataSourceImpl : DataSource(), NotificationTargetDataSource {

    override suspend fun getEmail(userId: Uuid): String? {
        return NotificationEmailTargetTable.select(NotificationEmailTargetTable.email)
            .where { NotificationEmailTargetTable.userId eq userId }
            .singleOrNull()
            ?.get(NotificationEmailTargetTable.email)
    }

    override suspend fun getTelegram(userId: Uuid): String? {
        return NotificationTelegramTargetTable.select(NotificationTelegramTargetTable.telegramTag)
            .where { NotificationTelegramTargetTable.userId eq userId }
            .singleOrNull()
            ?.get(NotificationTelegramTargetTable.telegramTag)
    }

    override suspend fun insertEmail(userId: Uuid, email: String, updatedAt: Instant) = dbQuery<Unit> {
        NotificationEmailTargetTable.insert {
            it[NotificationEmailTargetTable.userId] = userId
            it[NotificationEmailTargetTable.email] = email
            it[NotificationEmailTargetTable.sourceUpdatedAt] = updatedAt
        }
    }

    override suspend fun updateEmail(userId: Uuid, email: String, updatedAt: Instant): Boolean = dbQuery {
        NotificationEmailTargetTable.update(
            where = {
                (NotificationEmailTargetTable.userId eq userId) and
                    (
                        NotificationEmailTargetTable.sourceUpdatedAt.isNull() or
                            (NotificationEmailTargetTable.sourceUpdatedAt less updatedAt)
                        )
            }
        ) {
            it[NotificationEmailTargetTable.email] = email
            it[NotificationEmailTargetTable.sourceUpdatedAt] = updatedAt
        } > 0
    }

    override suspend fun insertTelegram(userId: Uuid, telegramTag: String) = dbQuery<Unit> {
        NotificationTelegramTargetTable.insert {
            it[NotificationTelegramTargetTable.userId] = userId
            it[NotificationTelegramTargetTable.telegramTag] = telegramTag
        }
    }

    override suspend fun updateTelegram(userId: Uuid, telegramTag: String): Boolean = dbQuery {
        NotificationTelegramTargetTable.update(
            where = { NotificationTelegramTargetTable.userId eq userId }
        ) {
            it[NotificationTelegramTargetTable.telegramTag] = telegramTag
        } > 0
    }

    override suspend fun deleteByUserId(userId: Uuid) = dbQuery<Unit> {
        NotificationEmailTargetTable.deleteWhere { NotificationEmailTargetTable.userId eq userId }
        NotificationTelegramTargetTable.deleteWhere { NotificationTelegramTargetTable.userId eq userId }
    }
}
