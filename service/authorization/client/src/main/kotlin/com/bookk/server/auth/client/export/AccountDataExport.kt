package com.bookk.server.auth.client.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant

@Serializable
data class AccountDataExport(
    @ProtoNumber(1) val createdAt: Instant,
    @ProtoNumber(2) val lastActiveAt: Instant,
    @ProtoNumber(3) val passkeys: List<PasskeyExport>,
    @ProtoNumber(4) val devices: List<DeviceExport>,
) {
    @Serializable
    data class PasskeyExport(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val createdAt: Instant,
        @ProtoNumber(3) val lastUsedAt: Instant,
        @ProtoNumber(4) val backedUp: Boolean,
    ) {
        companion object {
            fun stub(
                name: String = "My key",
                createdAt: Instant = Instant.fromEpochMilliseconds(0),
                lastUsedAt: Instant = Instant.fromEpochMilliseconds(0),
                backedUp: Boolean = false,
            ) = PasskeyExport(name, createdAt, lastUsedAt, backedUp)
        }
    }

    @Serializable
    data class DeviceExport(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val signedIn: Boolean,
        @ProtoNumber(3) val lastSignInAt: Instant?,
    ) {
        companion object {
            fun stub(
                name: String = "Pixel 9",
                signedIn: Boolean = true,
                lastSignInAt: Instant? = null,
            ) = DeviceExport(name, signedIn, lastSignInAt)
        }
    }

    companion object {
        fun stub(
            createdAt: Instant = Instant.fromEpochMilliseconds(0),
            lastActiveAt: Instant = Instant.fromEpochMilliseconds(0),
            passkeys: List<PasskeyExport> = emptyList(),
            devices: List<DeviceExport> = emptyList(),
        ) = AccountDataExport(createdAt, lastActiveAt, passkeys, devices)
    }
}
