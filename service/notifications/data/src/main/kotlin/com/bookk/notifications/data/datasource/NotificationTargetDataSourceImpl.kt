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

internal class NotificationTargetDataSourceImpl(
    private val targetCipher: TargetCipher,
) : DataSource(), NotificationTargetDataSource {

    override suspend fun getEmail(userId: Uuid): String? = dbQuery {
        val ciphertext = NotificationEmailTargetTable
            .select(NotificationEmailTargetTable.emailEncrypted)
            .where { NotificationEmailTargetTable.userId eq userId }
            .singleOrNull()
            ?.get(NotificationEmailTargetTable.emailEncrypted)
            ?: return@dbQuery null
        targetCipher.decrypt(userId, ciphertext, NotificationEmailTargetTable.tableName)
    }

    override suspend fun getTelegram(userId: Uuid): String? = dbQuery {
        val ciphertext = NotificationTelegramTargetTable
            .select(NotificationTelegramTargetTable.telegramTagEncrypted)
            .where { NotificationTelegramTargetTable.userId eq userId }
            .singleOrNull()
            ?.get(NotificationTelegramTargetTable.telegramTagEncrypted)
            ?: return@dbQuery null
        targetCipher.decrypt(userId, ciphertext, NotificationTelegramTargetTable.tableName)
    }

    override suspend fun hasEmail(userId: Uuid): Boolean = dbQuery {
        NotificationEmailTargetTable.select(NotificationEmailTargetTable.id)
            .where { NotificationEmailTargetTable.userId eq userId }
            .any()
    }

    override suspend fun hasTelegram(userId: Uuid): Boolean = dbQuery {
        NotificationTelegramTargetTable.select(NotificationTelegramTargetTable.id)
            .where { NotificationTelegramTargetTable.userId eq userId }
            .any()
    }

    override suspend fun insertEmail(userId: Uuid, email: String, updatedAt: Instant) = dbQuery<Unit> {
        val ciphertext = targetCipher.encrypt(userId, email) ?: return@dbQuery
        NotificationEmailTargetTable.insert {
            it[NotificationEmailTargetTable.userId] = userId
            it[NotificationEmailTargetTable.emailEncrypted] = ciphertext
            it[NotificationEmailTargetTable.sourceUpdatedAt] = updatedAt
        }
    }

    override suspend fun updateEmail(userId: Uuid, email: String, updatedAt: Instant): Boolean = dbQuery {
        val ciphertext = targetCipher.encrypt(userId, email) ?: return@dbQuery false
        val sourceUpdatedAt = NotificationEmailTargetTable.sourceUpdatedAt
        val isOutdated = sourceUpdatedAt.isNull() or (sourceUpdatedAt less updatedAt)
        NotificationEmailTargetTable.update(
            where = { (NotificationEmailTargetTable.userId eq userId) and isOutdated },
        ) {
            it[NotificationEmailTargetTable.emailEncrypted] = ciphertext
            it[NotificationEmailTargetTable.sourceUpdatedAt] = updatedAt
        } > 0
    }

    override suspend fun insertTelegram(userId: Uuid, telegramUserId: String) = dbQuery<Unit> {
        val ciphertext = targetCipher.encrypt(userId, telegramUserId) ?: return@dbQuery
        NotificationTelegramTargetTable.insert {
            it[NotificationTelegramTargetTable.userId] = userId
            it[NotificationTelegramTargetTable.telegramTagEncrypted] = ciphertext
        }
    }

    override suspend fun updateTelegram(userId: Uuid, telegramUserId: String): Boolean = dbQuery {
        val ciphertext = targetCipher.encrypt(userId, telegramUserId) ?: return@dbQuery false
        NotificationTelegramTargetTable.update(
            where = { NotificationTelegramTargetTable.userId eq userId },
        ) {
            it[NotificationTelegramTargetTable.telegramTagEncrypted] = ciphertext
        } > 0
    }

    override suspend fun deleteByUserId(userId: Uuid) = dbQuery<Unit> {
        NotificationEmailTargetTable.deleteWhere { NotificationEmailTargetTable.userId eq userId }
        NotificationTelegramTargetTable.deleteWhere { NotificationTelegramTargetTable.userId eq userId }
    }
}
