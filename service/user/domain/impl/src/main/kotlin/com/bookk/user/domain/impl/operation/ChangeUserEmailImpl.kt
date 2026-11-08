package com.bookk.user.domain.impl.operation

import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.onConstraintFailure
import com.bookk.server.user.client.api.event.UserEvent
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.operation.ChangeUserEmail
import com.bookk.user.domain.api.operation.ChangeUserEmail.Error
import com.bookk.user.domain.datasource.UserDataSource
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ChangeUserEmailImpl(
    private val userDataSource: UserDataSource,
    private val transactionManager: TransactionManager,
    private val eventProducer: StandardEventProducer,
) : ChangeUserEmail {

    override suspend fun invoke(userId: Uuid, verifiedEmail: String): Result<Unit> = transactionManager.transaction {
        val updatedAt = Clock.System.now()
        val changed = userDataSource.changeEmail(userId, verifiedEmail, updatedAt) ?: throw Error.UserNotFound()
        eventProducer.send(changed.updatedEvent(updatedAt))
    }.onConstraintFailure { throw Error.EmailTaken() }
}

internal fun User.updatedEvent(updatedAt: Instant) = UserEvent.Updated(
    userId = id,
    name = name,
    lastName = lastName,
    email = email,
    phone = phone,
    updatedAt = updatedAt,
)
