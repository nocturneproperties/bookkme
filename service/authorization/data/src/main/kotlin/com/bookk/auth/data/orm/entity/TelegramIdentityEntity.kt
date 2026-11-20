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
    var createdAt by TelegramIdentityTable.createdAt

    companion object : DecoratorUuidEntityClass<TelegramIdentityEntity>(TelegramIdentityTable) {
        fun new(authId: Uuid, telegramIdIndex: String): TelegramIdentityEntity {
            return new {
                account = AuthenticationEntity[authId]
                this.telegramIdIndex = telegramIdIndex
            }
        }

        fun findByTelegramIdIndex(telegramIdIndex: String): TelegramIdentityEntity? {
            return find { TelegramIdentityTable.telegramIdIndex eq telegramIdIndex }.singleOrNull()
        }
    }
}
