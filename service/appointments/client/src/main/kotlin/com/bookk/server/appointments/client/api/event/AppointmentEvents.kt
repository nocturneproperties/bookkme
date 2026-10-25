package com.bookk.server.appointments.client.api.event

import com.bookk.core.data.eventstreaming.EventStreaming
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface AppointmentEvent : EventStreaming.Event<String> {

    @Serializable
    data class RequestCreated(
        @ProtoNumber(1) val clientUserId: Uuid,
        @ProtoNumber(2) val clientName: String,
        @ProtoNumber(3) val employeeUserId: Uuid,
        @ProtoNumber(4) val employeeName: String,
        @ProtoNumber(5) val from: Instant,
        @ProtoNumber(6) val to: Instant,
        @ProtoNumber(7) val timeZone: TimeZone,
        @ProtoNumber(8) val businessName: String,
        @ProtoNumber(9) val address: String,
        @ProtoNumber(10) val price: String,
        @ProtoNumber(11) override val idempotencyKey: String = Uuid.random().toString()
    ) : AppointmentEvent {
        @ProtoNumber(12)
        override val topic: String = TOPIC

        companion object {
            const val TOPIC = "appointment.request_created"
        }
    }

    @Serializable
    data class RequestApproved(
        @ProtoNumber(1) val clientUserId: Uuid,
        @ProtoNumber(2) val clientName: String,
        @ProtoNumber(3) val employeeUserId: Uuid,
        @ProtoNumber(4) val employeeName: String,
        @ProtoNumber(5) val from: Instant,
        @ProtoNumber(6) val to: Instant,
        @ProtoNumber(7) val timeZone: TimeZone,
        @ProtoNumber(8) val businessName: String,
        @ProtoNumber(9) val address: String,
        @ProtoNumber(10) val price: String,
        @ProtoNumber(11) override val idempotencyKey: String = Uuid.random().toString()
    ) : AppointmentEvent {
        @ProtoNumber(12)
        override val topic: String = TOPIC

        companion object {
            const val TOPIC = "appointment.request_approved"
        }
    }

    @Serializable
    data class RequestRejected(
        @ProtoNumber(1) val clientUserId: Uuid,
        @ProtoNumber(2) val clientName: String,
        @ProtoNumber(3) val employeeUserId: Uuid,
        @ProtoNumber(4) val employeeName: String,
        @ProtoNumber(5) val from: Instant,
        @ProtoNumber(6) val to: Instant,
        @ProtoNumber(7) val timeZone: TimeZone,
        @ProtoNumber(8) val address: String,
        @ProtoNumber(9) val businessName: String,
        @ProtoNumber(10) val price: String,
        @ProtoNumber(11) val declineReason: String,
        @ProtoNumber(12) override val idempotencyKey: String = Uuid.random().toString()
    ) : AppointmentEvent {
        @ProtoNumber(13)
        override val topic: String = TOPIC

        companion object {
            const val TOPIC = "appointment.request_rejected"
        }
    }

    @Serializable
    data class Cancelled(
        @ProtoNumber(1) val clientUserId: Uuid,
        @ProtoNumber(2) val clientName: String,
        @ProtoNumber(3) val employeeUserId: Uuid,
        @ProtoNumber(4) val employeeName: String,
        @ProtoNumber(5) val from: Instant,
        @ProtoNumber(6) val to: Instant,
        @ProtoNumber(7) val timeZone: TimeZone,
        @ProtoNumber(8) val address: String,
        @ProtoNumber(9) val businessName: String,
        @ProtoNumber(10) val price: String,
        @ProtoNumber(11) val reason: String,
        @ProtoNumber(12) override val idempotencyKey: String = Uuid.random().toString()
    ) : AppointmentEvent {
        @ProtoNumber(13)
        override val topic: String = TOPIC

        companion object {
            const val TOPIC = "appointment.cancelled"
        }
    }
}