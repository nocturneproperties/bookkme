package com.bookk.business.domain.impl.operation.user

import com.bookk.business.domain.api.user.operation.SyncUserProfile
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.server.business.client.api.event.BusinessEvent
import library.encryption.SubjectKeys
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class SyncUserProfileImpl(
    private val transactionManager: TransactionManager,
    private val clientDataSource: ClientDataSource,
    private val employeeDataSource: EmployeeDataSource,
    private val eventProducer: StandardEventProducer,
    private val subjectKeys: SubjectKeys,
) : SyncUserProfile {
    override suspend fun invoke(
        userId: Uuid,
        name: String,
        lastName: String,
        email: String,
        phone: String?,
        updatedAt: Instant,
    ): Result<Unit> = transactionManager.transaction {
        val merges = clientDataSource.findClientMergesForUser(userId, email, updatedAt)
        val mergedClientIds = merges.map { it.fromClientId }
        clientDataSource.deleteClients(mergedClientIds)
        merges.groupBy { it.businessId to it.toClientId }.forEach { (kept, mergedInto) ->
            val (businessId, keptClientId) = kept
            eventProducer.send(
                BusinessEvent.ClientLinked(
                    businessId,
                    clientId = keptClientId,
                    userId = userId,
                    mergedClientIds = mergedInto.map { it.fromClientId },
                ),
            )
        }
        clientDataSource.updateIntegratedClients(userId, name, lastName, email, phone, updatedAt)
        employeeDataSource.updateIntegratedEmployees(userId, name, lastName, email, phone, updatedAt)
        subjectKeys.shredAll(mergedClientIds)
    }
}
