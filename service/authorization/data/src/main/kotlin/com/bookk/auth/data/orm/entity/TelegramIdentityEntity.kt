package com.bookk.auth.data.orm.entity

import com.bookk.auth.data.orm.table.TelegramIdentityTable
import com.bookk.core.data.DecoratorUuidEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.uuid.Uuid

internal class TelegramIdentityEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var account by AuthenticationEntity referencedOn TelegramIdentityTable.authId
    var telegramIdIndex by TelegramIdentityTable.telegramIdIndex
    var usernameIndex by TelegramIdentityTable.usernameIndex
    var createdAt by TelegramIdentityTable.createdAt

    companion object : DecoratorUuidEntityClass<TelegramIdentityEntity>(TelegramIdentityTable) {
        fun new(authId: Uuid, telegramIdIndex: String, usernameIndex: String): TelegramIdentityEntity {
            return new {
                account = AuthenticationEntity[authId]
                this.telegramIdIndex = telegramIdIndex
                this.usernameIndex = usernameIndex
            }
        }

        fun findByTelegramIdIndex(telegramIdIndex: String): TelegramIdentityEntity? {
            return find { TelegramIdentityTable.telegramIdIndex eq telegramIdIndex }.singleOrNull()
        }

        fun findByAuthId(authId: Uuid): TelegramIdentityEntity? {
            return find { TelegramIdentityTable.authId eq authId }.singleOrNull()
        }

        fun findByAuthIdAndUpdateUsernameIndex(authId: Uuid, usernameIndex: String): TelegramIdentityEntity? {
            return findByAuthId(authId)?.apply { this.usernameIndex = usernameIndex }
        }
    }
}
