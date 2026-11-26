package com.bookk.user.domain.impl.operation

import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.user.domain.api.operation.ChangeTelegramUsername
import com.bookk.user.domain.api.operation.ChangeTelegramUsername.Error
import com.bookk.user.domain.datasource.UserDataSource
import library.validation.TelegramUsernameValidator
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class ChangeTelegramUsernameImpl(
    private val userDataSource: UserDataSource,
    private val transactionManager: TransactionManager,
    private val eventProducer: StandardEventProducer,
) : ChangeTelegramUsername {

    override suspend fun invoke(userId: Uuid, username: String?): Result<Unit> {
        if (username != null && !TelegramUsernameValidator.isValid(username)) {
            return Result.failure(Error.UsernameInvalid())
        }
        return transactionManager.transaction {
            val updatedAt = Clock.System.now()
            val change = userDataSource.changeTelegramUsername(userId, username, updatedAt)
                ?: throw Error.UserNotFound()
            if (change.changed) eventProducer.send(change.user.updatedEvent(updatedAt))
        }
    }
}
