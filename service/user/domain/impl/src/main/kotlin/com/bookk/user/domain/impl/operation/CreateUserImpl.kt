package com.bookk.user.domain.impl.operation

import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.user.domain.api.entity.LegalDocumentAcceptance
import com.bookk.user.domain.api.entity.LegalDocumentVersion
import com.bookk.user.domain.api.entity.NewUser
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.entity.UserId
import com.bookk.user.domain.api.operation.CreateUser
import com.bookk.user.domain.datasource.LegalDocumentDataSource
import com.bookk.user.domain.datasource.UserDataSource
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class CreateUserImpl(
    private val userDataSource: UserDataSource,
    private val legalDocumentDataSource: LegalDocumentDataSource,
    private val transactionManager: TransactionManager,
    private val eventProducer: StandardEventProducer,
) : CreateUser {

    override suspend fun invoke(newUser: NewUser): Result<UserId> = transactionManager.transaction {
        val now = Clock.System.now()
        val acceptances = legalDocumentDataSource.publishedAcceptances(newUser.acceptedDocuments, now)
        val versionsInEffect = legalDocumentDataSource.getVersionsInEffect(now)
        if (!versionsInEffect.all { it.isAcceptedIn(acceptances) }) throw CreateUser.Error.LegalDocumentsNotAccepted()
        val created = userDataSource.insertNewUser(
            User(
                id = Uuid.random(),
                name = newUser.name,
                lastName = newUser.lastName,
                email = newUser.email,
                phone = null,
            ),
        )
        acceptances.forEach { legalDocumentDataSource.recordAcceptance(created.id, it) }
        eventProducer.send(created.updatedEvent(now))
        UserId(created.id)
    }

    private fun LegalDocumentVersion.isAcceptedIn(acceptances: List<LegalDocumentAcceptance>): Boolean {
        return acceptances.any { it.document == document && it.version == version }
    }
}
