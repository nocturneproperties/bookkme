package com.bookk.auth.domain.impl.operation.device

import com.bookk.auth.domain.api.device.operation.DeleteInactiveDevices
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.server.auth.client.AuthEvent
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

internal class DeleteInactiveDevicesImpl(
    private val deviceDataSource: DeviceDataSource,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
) : DeleteInactiveDevices {

    override suspend fun invoke(): Result<Unit> = transactionManager.transaction {
        val deletedDevices = deviceDataSource.deleteInactiveDevices(
            olderThan = Clock.System.now().minus(INACTIVE_THRESHOLD),
        )
        deletedDevices.forEach { device ->
            eventProducer.send(AuthEvent.DeviceDeleted(device.deviceUuid, authId = device.authId))
        }
    }

    companion object {
        private val INACTIVE_THRESHOLD = 30.days
    }
}
