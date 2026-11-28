package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.WithdrawAppointmentRequest
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import kotlin.uuid.Uuid

internal class WithdrawAppointmentRequestImpl(
    private val requestDataSource: AppointmentRequestDataSource,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
) : WithdrawAppointmentRequest {
    override suspend fun invoke(userId: Uuid, requestId: Uuid): Result<Unit> = transactionManager.transaction {
        val request = requestDataSource.getForUpdate(requestId) ?: throw Error.NotFound()
        if (request.initiatorUserId != userId) throw Error.NotFound()
        request.requirePending()
        requestDataSource.withdraw(requestId)
        eventProducer.send(AppointmentEvent.RequestResolved(request.id, request.employee.userId))
    }
}
