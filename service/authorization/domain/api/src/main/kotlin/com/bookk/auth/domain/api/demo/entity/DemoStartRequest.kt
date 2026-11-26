package com.bookk.auth.domain.api.demo.entity

import com.bookk.user.domain.api.entity.AcceptedLegalDocuments
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class DemoStartRequest(
    @ProtoNumber(1) val deviceInfo: DeviceInfo,
    @ProtoNumber(2) val acceptedDocuments: AcceptedLegalDocuments,
    @ProtoNumber(3) val timeZone: String,
) {
    @Serializable
    data class DeviceInfo(
        @ProtoNumber(1) val deviceUUID: Uuid,
        @ProtoNumber(2) val deviceName: String,
    )

    companion object {
        fun stub(
            deviceInfo: DeviceInfo = DeviceInfo(Uuid.random(), "Pixel 9"),
            acceptedDocuments: AcceptedLegalDocuments = AcceptedLegalDocuments("2026-01-01", "2025-06-01"),
            timeZone: String = "Europe/Warsaw",
        ): DemoStartRequest {
            return DemoStartRequest(deviceInfo, acceptedDocuments, timeZone)
        }
    }
}
