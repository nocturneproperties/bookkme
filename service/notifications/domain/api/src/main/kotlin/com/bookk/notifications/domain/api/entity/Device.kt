package com.bookk.notifications.domain.api.entity

import com.bookk.core.domain.entity.Language
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class Device(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val authId: Uuid,
    @ProtoNumber(3) val deviceUuid: Uuid,
    @ProtoNumber(4) val userId: Uuid,
    @ProtoNumber(5) val notificationToken: String?,
    @ProtoNumber(6) val language: Language,
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            authId: Uuid = Uuid.random(),
            deviceId: Uuid = Uuid.random(),
            userId: Uuid = Uuid.random(),
            notificationToken: String? = null,
            language: Language = Language.EN,
        ) = Device(id, authId, deviceId, userId, notificationToken, language)
    }
}
