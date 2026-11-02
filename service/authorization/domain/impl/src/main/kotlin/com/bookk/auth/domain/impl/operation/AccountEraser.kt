package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.server.auth.client.AuthEvent
import kotlin.uuid.Uuid

internal class AccountEraser(
    private val accountDataSource: AccountDataSource,
    private val deviceDataSource: DeviceDataSource,
    private val eventProducer: StandardEventProducer,
) {
    suspend fun erase(authId: Uuid, userId: Uuid) {
        val devices = deviceDataSource.getDevices(authId)
        eventProducer.send(AuthEvent.UserDeleted(userId))
        devices.forEach { eventProducer.send(AuthEvent.DeviceDeleted(it.deviceInfo.deviceUUID)) }
        accountDataSource.deleteAuthorization(authId)
    }
}
