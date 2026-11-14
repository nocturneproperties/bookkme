package com.bookk.notifications.data.datasource

import com.bookk.core.data.DataSource
import com.bookk.notifications.data.orm.entity.NotificationEmailTargetEntity
import com.bookk.notifications.data.orm.entity.NotificationTelegramTargetEntity
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
        val row = NotificationEmailTargetTable
            .select(NotificationEmailTargetTable.email, NotificationEmailTargetTable.emailEncrypted)
            .where { NotificationEmailTargetTable.userId eq userId }
            .singleOrNull()
            ?: return@dbQuery null
        val ciphertext = row[NotificationEmailTargetTable.emailEncrypted]
            ?: return@dbQuery row[NotificationEmailTargetTable.email]
        targetCipher.decrypt(userId, ciphertext, NotificationEmailTargetTable.tableName)
    }

    override suspend fun getTelegram(userId: Uuid): String? = dbQuery {
        val row = NotificationTelegramTargetTable
            .select(NotificationTelegramTargetTable.telegramTag, NotificationTelegramTargetTable.telegramTagEncrypted)
            .where { NotificationTelegramTargetTable.userId eq userId }
            .singleOrNull()
            ?: return@dbQuery null
        val ciphertext = row[NotificationTelegramTargetTable.telegramTagEncrypted]
            ?: return@dbQuery row[NotificationTelegramTargetTable.telegramTag]
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
        NotificationEmailTargetTable.update(
            where = {
                (NotificationEmailTargetTable.userId eq userId) and
                    (
                        NotificationEmailTargetTable.sourceUpdatedAt.isNull() or
                            (NotificationEmailTargetTable.sourceUpdatedAt less updatedAt)
                        )
            },
        ) {
            it[NotificationEmailTargetTable.email] = null
            it[NotificationEmailTargetTable.emailEncrypted] = ciphertext
            it[NotificationEmailTargetTable.sourceUpdatedAt] = updatedAt
        } > 0
    }

    override suspend fun insertTelegram(userId: Uuid, telegramTag: String) = dbQuery<Unit> {
        val ciphertext = targetCipher.encrypt(userId, telegramTag) ?: return@dbQuery
        NotificationTelegramTargetTable.insert {
            it[NotificationTelegramTargetTable.userId] = userId
            it[NotificationTelegramTargetTable.telegramTagEncrypted] = ciphertext
        }
    }

    override suspend fun updateTelegram(userId: Uuid, telegramTag: String): Boolean = dbQuery {
        val ciphertext = targetCipher.encrypt(userId, telegramTag) ?: return@dbQuery false
        NotificationTelegramTargetTable.update(
            where = { NotificationTelegramTargetTable.userId eq userId },
        ) {
            it[NotificationTelegramTargetTable.telegramTag] = null
            it[NotificationTelegramTargetTable.telegramTagEncrypted] = ciphertext
        } > 0
    }

    override suspend fun deleteByUserId(userId: Uuid) = dbQuery<Unit> {
        NotificationEmailTargetTable.deleteWhere { NotificationEmailTargetTable.userId eq userId }
        NotificationTelegramTargetTable.deleteWhere { NotificationTelegramTargetTable.userId eq userId }
    }

    override suspend fun encryptPlaintextEmails(limit: Int): Int = dbQuery {
        val plaintext = NotificationEmailTargetEntity
            .find { NotificationEmailTargetTable.emailEncrypted.isNull() }
            .limit(limit)
            .forUpdate()
            .toList()
        plaintext.forEach { target ->
            target.emailEncrypted = encryptStored(target.userId, checkNotNull(target.email))
            target.email = null
        }
        plaintext.size
    }

    override suspend fun encryptPlaintextTelegramTags(limit: Int): Int = dbQuery {
        val plaintext = NotificationTelegramTargetEntity
            .find { NotificationTelegramTargetTable.telegramTagEncrypted.isNull() }
            .limit(limit)
            .forUpdate()
            .toList()
        plaintext.forEach { target ->
            target.telegramTagEncrypted = encryptStored(target.userId, checkNotNull(target.telegramTag))
            target.telegramTag = null
        }
        plaintext.size
    }

    private suspend fun encryptStored(userId: Uuid, target: String): ByteArray {
        return checkNotNull(targetCipher.encrypt(userId, target)) {
            "The stored target of user $userId has a shredded key"
        }
    }
}
