package com.bookk.appointments.domain.api.entity

import com.bookk.library.serializer.MoneySerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import org.joda.money.Money
import kotlin.time.Duration
import kotlin.uuid.Uuid

@Serializable
data class ServiceSnapshot(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val groupId: Uuid,
    @ProtoNumber(4)
    @Serializable(with = MoneySerializer::class)
    val price: Money,
    @ProtoNumber(5) val duration: Duration
) {
    companion object {
        fun stub() = ServiceSnapshot(
            id = Uuid.random(),
            name = "Service Name",
            groupId = Uuid.random(),
            price = Money.parse("USD 100"),
            duration = Duration.parse("30m")
        )
    }
}