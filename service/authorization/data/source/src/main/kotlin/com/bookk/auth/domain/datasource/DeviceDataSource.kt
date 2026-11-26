package com.bookk.auth.domain.datasource

import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.auth.domain.api.identification.entity.DeviceIdentity
import com.bookk.core.domain.entity.Language
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface DeviceDataSource {
    suspend fun insertDevice(authId: Uuid, uuid: Uuid, name: String, language: Language): Uuid?
    suspend fun updateLanguage(authId: Uuid, deviceUuid: Uuid, language: Language)
    suspend fun getDeviceById(deviceId: Uuid): Device?
    suspend fun getDeviceByIdForUpdate(deviceId: Uuid): Device?
    suspend fun getDeviceByRefreshTokenIdForUpdate(tokenId: Uuid): Device?
    suspend fun getDeviceByAuthIdAndUUIDForUpdate(authId: Uuid, deviceUUID: Uuid): Device?
    suspend fun getDevices(authId: Uuid): List<Device>
    suspend fun attachRefreshTokenToDevice(deviceId: Uuid, tokenId: Uuid, tokenHash: String, expiresAt: Instant)
    suspend fun rotateRefreshToken(deviceId: Uuid, tokenId: Uuid, tokenHash: String, expiresAt: Instant)
    suspend fun deleteTokenFromDevice(deviceId: Uuid)
    suspend fun deleteInactiveDevices(olderThan: Instant): List<DeviceIdentity>
}
