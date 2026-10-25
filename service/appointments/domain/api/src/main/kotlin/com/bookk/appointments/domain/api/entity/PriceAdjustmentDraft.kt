package com.bookk.appointments.domain.api.entity

import com.bookk.library.serializer.MoneySerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import org.joda.money.Money
import kotlin.uuid.Uuid

@Serializable
data class PriceAdjustmentDraft(
    @ProtoNumber(1) val additionalServiceIds: List<Uuid>,
    @ProtoNumber(2)
    @Serializable(with = MoneySerializer::class)
    val price: Money,
    @ProtoNumber(3) val reason: String?
) {
    companion object {
        const val REASON_MAX_LENGTH = 2048

        fun stub(
            additionalServiceIds: List<Uuid> = emptyList(),
            price: Money = Money.parse("USD 150"),
            reason: String? = "Reason"
        ) = PriceAdjustmentDraft(additionalServiceIds = additionalServiceIds, price = price, reason = reason)
    }
}
