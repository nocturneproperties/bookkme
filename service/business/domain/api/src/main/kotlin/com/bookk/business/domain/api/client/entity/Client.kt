package com.bookk.business.domain.api.client.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
class ClientRemote(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val lastName: String,
    @ProtoNumber(4) val phone: String?,
    @ProtoNumber(5) val email: String?,
    @ProtoNumber(6) val userId: Uuid?,
    @ProtoNumber(7) val description: String?
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            name: String = "stub-name",
            lastName: String = "stub-lastname",
            phone: String? = "+10000000000",
            email: String? = "stub@client.com",
            userId: Uuid? = null,
            description: String? = null
        ) = ClientRemote(
            id = id,
            name = name,
            lastName = lastName,
            phone = phone,
            email = email,
            userId = userId,
            description = description
        )
    }
}

sealed interface Client {
    val id: Uuid
    val name: String
    val lastName: String
    val phone: String?
    val email: String?
    val description: String?

    data class Detached(
        override val id: Uuid,
        override val name: String,
        override val lastName: String,
        override val phone: String?,
        override val email: String?,
        override val description: String? = null
    ) : Client {
        companion object {
            fun stub(
                id: Uuid = Uuid.random(),
                name: String = "stub-name",
                lastName: String = "stub-lastname",
                phone: String? = "+10000000000",
                email: String? = "stub@client.com",
                description: String? = null
            ) = Detached(
                id = id,
                name = name,
                lastName = lastName,
                phone = phone,
                email = email,
                description = description
            )
        }
    }

    data class Integrated(
        override val id: Uuid,
        override val name: String,
        override val lastName: String,
        override val phone: String?,
        override val email: String?,
        val userId: Uuid,
        override val description: String? = null
    ) : Client {
        companion object {
            fun stub(
                id: Uuid = Uuid.random(),
                name: String = "stub-name",
                lastName: String = "stub-lastname",
                phone: String? = "+10000000000",
                email: String? = "stub@client.com",
                userId: Uuid = Uuid.random(),
                description: String? = null
            ) = Integrated(
                id = id,
                name = name,
                lastName = lastName,
                phone = phone,
                email = email,
                userId = userId,
                description = description
            )
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
        description = description
    )
}

fun ClientRemote.toDomain(): Client {
    return if (userId == null) {
        Client.Detached(
            id = id,
            name = name,
            lastName = lastName,
            phone = phone,
            email = email,
            description = description
        )
    } else {
        Client.Integrated(
            id = id,
            name = name,
            lastName = lastName,
            phone = phone,
            email = email,
            userId = userId,
            description = description
        )
    }
}
