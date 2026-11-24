package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.server.auth.client.AuthEvent
import kotlin.time.Clock

internal class DeviceSignOut(
    private val deviceDataSource: DeviceDataSource,
    private val eventProducer: StandardEventProducer,
) {
    suspend fun signOut(device: Device) {
        deviceDataSource.deleteTokenFromDevice(device.deviceInfo.id)
        eventProducer.send(
            AuthEvent.DeviceSignedOut(
                authId = device.authRecord.id,
                deviceUuid = device.deviceInfo.deviceUUID,
                signedOutAt = Clock.System.now(),
            ),
        )
    }
}
