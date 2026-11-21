package com.bookk.appointments.domain.api.entity

import com.bookk.library.serializer.MoneySerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import org.joda.money.Money

@Serializable
data class PriceAdjustment(
    @ProtoNumber(2)
    @Serializable(with = MoneySerializer::class)
    val price: Money,
    @ProtoNumber(3) val reason: String?,
    @ProtoNumber(4) val additionalServices: List<AdditionalService> = emptyList(),
) {
    companion object {
        fun stub(
            additionalServices: List<AdditionalService> = listOf(AdditionalService.stub()),
            price: Money = Money.parse("USD 150"),
            reason: String? = "Reason",
        ): PriceAdjustment {
            return PriceAdjustment(price = price, reason = reason, additionalServices = additionalServices)
        }
    }
}
