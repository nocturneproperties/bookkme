package com.bookk.user.domain.impl.operation

import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.onConstraintFailure
import com.bookk.user.domain.api.entity.UserEditModel
import com.bookk.user.domain.api.operation.EditUser
import com.bookk.user.domain.api.operation.EditUser.Error
import com.bookk.user.domain.datasource.UserDataSource
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class EditUserImpl(
    private val userDataSource: UserDataSource,
    private val transactionManager: TransactionManager,
    private val eventProducer: StandardEventProducer,
) : EditUser {
    override suspend fun invoke(id: Uuid, user: UserEditModel): Result<Unit> = transactionManager.transaction {
        val updatedAt = Clock.System.now()
        val updated = userDataSource.updateUser(id, user, updatedAt) ?: throw Error.UserNotFound()
        val emailChanged = user.email != null && user.email != updated.email
        if (emailChanged) throw Error.EmailChangeRequiresVerification()
        eventProducer.send(updated.updatedEvent(updatedAt))
    }.onConstraintFailure { throw Error.ContactTaken() }
}
