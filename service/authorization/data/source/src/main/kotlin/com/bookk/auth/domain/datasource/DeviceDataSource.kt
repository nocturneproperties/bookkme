package com.bookk.auth.domain.datasource

import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.core.domain.entity.Language
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface DeviceDataSource {
    suspend fun insertDevice(authId: Uuid, uuid: Uuid, name: String, language: Language): Uuid?
    suspend fun updateLanguage(authId: Uuid, deviceUuid: Uuid, language: Language)
    suspend fun getDeviceById(deviceId: Uuid): Device?
    suspend fun getDeviceByRefreshTokenId(tokenId: Uuid): Device?
    suspend fun getDeviceByAuthIdAndUUID(authId: Uuid, deviceUUID: Uuid): Device?
    suspend fun getDevices(authId: Uuid): List<Device>
    suspend fun attachRefreshTokenToDevice(deviceId: Uuid, tokenId: Uuid, tokenHash: String)
    suspend fun rotateRefreshToken(deviceId: Uuid, tokenId: Uuid, tokenHash: String)
    suspend fun deleteTokenFromDevice(deviceId: Uuid)
    suspend fun deleteInactiveDevices(olderThan: Instant): List<Uuid>
}