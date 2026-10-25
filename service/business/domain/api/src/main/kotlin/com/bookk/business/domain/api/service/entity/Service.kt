package com.bookk.business.domain.api.service.entity

import com.bookk.library.serializer.MoneySerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import org.joda.money.Money
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Service(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val businessId: Uuid,
    @ProtoNumber(3) val group: ServiceGroup,
    @ProtoNumber(4) val name: String,
    @ProtoNumber(5) val duration: Duration,
    @ProtoNumber(6)
    @Serializable(with = MoneySerializer::class)
    val price: Money,
    @ProtoNumber(7) val isAvailable: Boolean,
    @ProtoNumber(8) val createdAt: Instant
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            businessId: Uuid = Uuid.random(),
            group: ServiceGroup = ServiceGroup.stub(businessId = businessId),
            name: String = "stub-service",
            duration: Duration = 30.minutes,
            price: Money = Money.parse("USD 50"),
            isAvailable: Boolean = true,
            createdAt: Instant = Instant.fromEpochMilliseconds(0)
        ) = Service(
            id = id,
            businessId = businessId,
            group = group,
            name = name,
            duration = duration,
            price = price,
            isAvailable = isAvailable,
            createdAt = createdAt
        )
    }
}