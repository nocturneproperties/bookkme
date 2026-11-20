package com.bookk.auth.domain.api.telegram.entity

import com.bookk.user.domain.api.entity.AcceptedLegalDocuments
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class TelegramSignInRequest(
    @ProtoNumber(1) val initData: String,
    @ProtoNumber(2) val deviceInfo: DeviceInfo,
    @ProtoNumber(3) val acceptedDocuments: AcceptedLegalDocuments?,
) {
    @Serializable
    data class DeviceInfo(
        @ProtoNumber(1) val deviceUUID: Uuid,
        @ProtoNumber(2) val deviceName: String,
    )

    companion object {
        fun stub(
            initData: String = "auth_date=0&hash=00&signature=AA&user=%7B%22id%22%3A1%7D",
            deviceInfo: DeviceInfo = DeviceInfo(Uuid.random(), "Telegram"),
            acceptedDocuments: AcceptedLegalDocuments? = null,
        ): TelegramSignInRequest {
            return TelegramSignInRequest(initData, deviceInfo, acceptedDocuments)
        }
    }
}
