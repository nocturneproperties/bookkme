package com.bookk.auth.data.datasource

import com.bookk.auth.data.map.toDomain
import com.bookk.auth.data.orm.entity.TelegramIdentityEntity
import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.datasource.TelegramIdentityDataSource
import com.bookk.core.data.DataSource
import library.encryption.BlindIndex
import kotlin.uuid.Uuid

internal class TelegramIdentityDataSourceImpl(
    private val blindIndex: BlindIndex,
) : DataSource(), TelegramIdentityDataSource {

    override suspend fun getAccountByTelegramUser(telegramUserId: Long): Authentication? = dbQuery {
        TelegramIdentityEntity.findByTelegramIdIndex(indexOf(telegramUserId))?.account?.toDomain()
    }

    override suspend fun link(authId: Uuid, telegramUserId: Long, username: String?): Unit = dbQuery {
        TelegramIdentityEntity.new(authId, indexOf(telegramUserId), usernameIndexOf(username))
    }

    override suspend fun isUsernameSynced(authId: Uuid, username: String?): Boolean = dbQuery {
        TelegramIdentityEntity.findByAuthId(authId)?.usernameIndex == usernameIndexOf(username)
    }

    override suspend fun markUsernameSynced(authId: Uuid, username: String?): Unit = dbQuery {
        TelegramIdentityEntity.findByAuthIdAndUpdateUsernameIndex(authId, usernameIndexOf(username))
    }

    private fun indexOf(telegramUserId: Long): String {
        return blindIndex.of(telegramUserId.toString())
    }

    private fun usernameIndexOf(username: String?): String {
        return blindIndex.of(username.orEmpty())
    }
}
