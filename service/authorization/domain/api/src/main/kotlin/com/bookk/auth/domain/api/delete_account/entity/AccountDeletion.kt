package com.bookk.auth.domain.api.delete_account.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant

@Serializable
data class AccountDeletion(
    @ProtoNumber(1) val deletesAt: Instant,
) {
    companion object {
        fun stub(deletesAt: Instant = Instant.fromEpochMilliseconds(0)): AccountDeletion {
            return AccountDeletion(deletesAt)
        }
    }
}

@Serializable
data class AccountDeletionStatus(
    @ProtoNumber(1) val deletesAt: Instant?,
) {
    companion object {
        fun stub(deletesAt: Instant? = null): AccountDeletionStatus {
            return AccountDeletionStatus(deletesAt)
        }
    }
}
