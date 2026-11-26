package com.bookk.user.domain.impl.operation

import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.onConstraintFailure
import com.bookk.user.domain.api.entity.ProfileName
import com.bookk.user.domain.api.operation.UpgradeDemoProfile
import com.bookk.user.domain.api.operation.UpgradeDemoProfile.Error
import com.bookk.user.domain.datasource.UserDataSource
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class UpgradeDemoProfileImpl(
    private val userDataSource: UserDataSource,
    private val transactionManager: TransactionManager,
    private val eventProducer: StandardEventProducer,
) : UpgradeDemoProfile {

    override suspend fun invoke(
        userId: Uuid,
        verifiedEmail: String,
        name: ProfileName,
    ): Result<Unit> = transactionManager.transaction {
        val updatedAt = Clock.System.now()
        val changed = userDataSource.changeEmailAndName(userId, verifiedEmail, name, updatedAt)
            ?: throw Error.UserNotFound()
        eventProducer.send(changed.updatedEvent(updatedAt))
    }.onConstraintFailure { throw Error.EmailTaken() }
}
