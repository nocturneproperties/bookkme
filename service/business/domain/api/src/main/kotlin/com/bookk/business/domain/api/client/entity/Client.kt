package com.bookk.business.domain.api.client.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
class ClientRemote(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val lastName: String,
    @ProtoNumber(4) val phone: String?,
    @ProtoNumber(5) val email: String?,
    @ProtoNumber(6) val userId: Uuid?,
    @ProtoNumber(7) val description: String?,
    @ProtoNumber(8) val createdAt: Instant?,
    @ProtoNumber(9) val telegramUsername: String?,
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            name: String = "stub-name",
            lastName: String = "stub-lastname",
            phone: String? = "+10000000000",
            email: String? = "stub@client.com",
            userId: Uuid? = null,
            description: String? = null,
            createdAt: Instant? = Instant.fromEpochMilliseconds(0),
            telegramUsername: String? = null,
        ): ClientRemote {
            return ClientRemote(
                id = id,
                name = name,
                lastName = lastName,
                phone = phone,
                email = email,
                userId = userId,
                description = description,
                createdAt = createdAt,
                telegramUsername = telegramUsername,
            )
        }
    }
}

sealed interface Client {
    val id: Uuid
    val name: String
    val lastName: String
    val phone: String?
    val email: String?
    val description: String?
    val createdAt: Instant?

    data class Detached(
        override val id: Uuid,
        override val name: String,
        override val lastName: String,
        override val phone: String?,
        override val email: String?,
        override val description: String? = null,
        override val createdAt: Instant? = null,
    ) : Client {
        companion object {
            fun stub(
                id: Uuid = Uuid.random(),
                name: String = "stub-name",
                lastName: String = "stub-lastname",
                phone: String? = "+10000000000",
                email: String? = "stub@client.com",
                description: String? = null,
                createdAt: Instant? = null,
            ): Detached {
                return Detached(
                    id = id,
                    name = name,
                    lastName = lastName,
                    phone = phone,
                    email = email,
                    description = description,
                    createdAt = createdAt,
                )
            }
        }
    }

    data class Integrated(
        override val id: Uuid,
        override val name: String,
        override val lastName: String,
        override val phone: String?,
        override val email: String?,
        val userId: Uuid,
        override val description: String? = null,
        override val createdAt: Instant? = null,
        val telegramUsername: String? = null,
    ) : Client {
        companion object {
            fun stub(
                id: Uuid = Uuid.random(),
                name: String = "stub-name",
                lastName: String = "stub-lastname",
                phone: String? = "+10000000000",
                email: String? = "stub@client.com",
                userId: Uuid = Uuid.random(),
                description: String? = null,
                createdAt: Instant? = null,
                telegramUsername: String? = null,
            ): Integrated {
                return Integrated(
                    id = id,
                    name = name,
                    lastName = lastName,
                    phone = phone,
                    email = email,
                    userId = userId,
                    description = description,
                    createdAt = createdAt,
                    telegramUsername = telegramUsername,
                )
            }
        }
    }

    companion object {
        const val MAX_DESCRIPTION_LENGTH = 1024
    }
}

fun Client.toRemote(): ClientRemote {
    return ClientRemote(
        id = id,
        name = name,
        lastName = lastName,
        phone = phone,
        email = email,
        userId = (this as? Client.Integrated)?.userId,
        description = description,
        createdAt = createdAt,
        telegramUsername = (this as? Client.Integrated)?.telegramUsername,
    )
}

fun ClientRemote.toDetachedClient(): Client.Detached {
    return Client.Detached(
        id = id,
        name = name,
        lastName = lastName,
        phone = phone,
        email = email,
        description = description,
    )
}
