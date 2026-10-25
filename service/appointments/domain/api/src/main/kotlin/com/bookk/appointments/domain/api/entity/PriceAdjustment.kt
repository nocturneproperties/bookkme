package com.bookk.appointments.domain.api.entity

import com.bookk.library.serializer.MoneySerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import org.joda.money.Money

@Serializable
data class PriceAdjustment(
    @ProtoNumber(1) val additionalServices: List<ServiceSnapshot>,
    @ProtoNumber(2)
    @Serializable(with = MoneySerializer::class)
    val price: Money,
    @ProtoNumber(3) val reason: String?
) {
    companion object {
        fun stub(
            additionalServices: List<ServiceSnapshot> = listOf(ServiceSnapshot.stub()),
            price: Money = Money.parse("USD 150"),
            reason: String? = "Reason"
        ) = PriceAdjustment(additionalServices = additionalServices, price = price, reason = reason)
    }
}
