package com.bookk.business.domain.api.business.entity

import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import library.schedule.Schedule
import kotlin.uuid.Uuid

@Serializable
data class Business(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val description: String,
    @ProtoNumber(4) val address: String,
    @ProtoNumber(5) val timeZone: TimeZone,
    @ProtoNumber(6) val location: Location?,
    @ProtoNumber(7) val currencyCode: String,
    @ProtoNumber(8) val socials: List<Social>,
    @ProtoNumber(9) val schedule: Schedule,
    @ProtoNumber(10) val permissions: BusinessPermissions,
    @ProtoNumber(11) val ownerId: Uuid,
    @ProtoNumber(12) val employeeId: Uuid?,
    @ProtoNumber(13) val clientId: Uuid?
) {
    fun viewedBy(viewer: BusinessViewer): Business = copy(
        permissions = viewer.permissions,
        employeeId = viewer.employeeId,
        clientId = viewer.clientId
    )

    @Serializable
    class Location(
        @ProtoNumber(1) val lat: Double,
        @ProtoNumber(2) val lng: Double
    )

    @Serializable
    data class Social(
        @ProtoNumber(1) val kind: SocialKind,
        @ProtoNumber(2) val value: String?
    )

    enum class SocialKind {
        PHONE,
        INSTAGRAM,
        TELEGRAM,
        VIBER,
        WHATSAPP
    }

    companion object {
        const val MAX_NAME_LENGTH = 512
        const val MAX_DESCRIPTION_LENGTH = 1024
        const val MAX_ADDRESS_LENGTH = 512
        const val MAX_SOCIAL_LENGTH = 256

        fun stub(
            id: Uuid = Uuid.random(),
            name: String = "Business name",
            description: String = "Business description",
            address: String = "Business address",
            timeZone: TimeZone = TimeZone.UTC,
            location: Location? = null,
            currencyCode: String = "USD",
            socials: List<Social> = emptyList(),
            schedule: Schedule = Schedule(),
            permissions: BusinessPermissions = BusinessPermissions.NONE,
            ownerId: Uuid = Uuid.random(),
            employeeId: Uuid? = null,
            clientId: Uuid? = null
        ) = Business(
            id = id,
            name = name,
            description = description,
            address = address,
            timeZone = timeZone,
            location = location,
            currencyCode = currencyCode,
            socials = socials,
            schedule = schedule,
            permissions = permissions,
            ownerId = ownerId,
            employeeId = employeeId,
            clientId = clientId
        )
    }
}
