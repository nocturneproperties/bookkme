package com.bookk.appointments.domain.api.entity

import com.bookk.library.serializer.MoneySerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import org.joda.money.Money

@Serializable
data class PriceAdjustmentDraft(
    @ProtoNumber(2)
    @Serializable(with = MoneySerializer::class)
    val price: Money,
    @ProtoNumber(3) val reason: String?,
    @ProtoNumber(4) val additionalServices: List<RequestedService> = emptyList(),
) {
    companion object {
        const val REASON_MAX_LENGTH = 2048
        const val MAX_ADDITIONAL_SERVICES = 100

        fun stub(
            additionalServices: List<RequestedService> = emptyList(),
            price: Money = Money.parse("USD 150"),
            reason: String? = "Reason",
        ): PriceAdjustmentDraft {
            return PriceAdjustmentDraft(price = price, reason = reason, additionalServices = additionalServices)
        }
    }
}
