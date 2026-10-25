package com.bookk.server.business.client.api.event

import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.server.business.client.api.BusinessDTO
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface BusinessEvent : EventStreaming.Event<String> {

    @Serializable
    data class Deleted(
        @ProtoNumber(1) val businessId: Uuid,
        @ProtoNumber(2) override val idempotencyKey: String = Uuid.random().toString()
    ) : BusinessEvent {
        @ProtoNumber(3)
        override val topic: String = TOPIC
        override val partitionKey: String get() = businessId.toString()

        companion object {
            const val TOPIC = "business.deleted"
        }
    }

    @Serializable
    data class Updated(
        @ProtoNumber(1) val business: BusinessDTO,
        @ProtoNumber(2) val updatedAt: Instant,
        @ProtoNumber(3) override val idempotencyKey: String = Uuid.random().toString()
    ) : BusinessEvent {
        @ProtoNumber(4)
        override val topic: String = TOPIC
        override val partitionKey: String get() = business.id.toString()

        companion object {
            const val TOPIC = "business.updated"
        }
    }

    @Serializable
    data class EmployeeInvitationRedeemed(
        @ProtoNumber(1) val inviterUserId: Uuid,
        @ProtoNumber(2) val employeeUserId: Uuid,
        @ProtoNumber(3) val employeeName: String,
        @ProtoNumber(4) val businessId: Uuid,
        @ProtoNumber(5) val businessName: String,
        @ProtoNumber(6) override val idempotencyKey: String = Uuid.random().toString()
    ) : BusinessEvent {
        @ProtoNumber(7)
        override val topic: String = TOPIC

        companion object {
            const val TOPIC = "business.employee_invitation_redeemed"
        }
    }

    @Serializable
    data class EmployeePermissionsChanged(
        @ProtoNumber(1) val employeeUserId: Uuid,
        @ProtoNumber(2) val businessId: Uuid,
        @ProtoNumber(3) val permissions: BusinessPermissions,
        @ProtoNumber(4) override val idempotencyKey: String = Uuid.random().toString(),
        @ProtoNumber(6) val suspended: Boolean = false
    ) : BusinessEvent {
        @ProtoNumber(5)
        override val topic: String = TOPIC
        override val partitionKey: String get() = businessId.toString()

        companion object {
            const val TOPIC = "business.employee_permissions_changed"
        }
    }
}