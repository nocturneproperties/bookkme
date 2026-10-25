package com.bookk.appointments.data.orm.entity

import com.bookk.appointments.data.orm.table.PriceAdjustmentServicesTable
import com.bookk.appointments.data.orm.table.PriceAdjustmentTable
import com.bookk.appointments.domain.api.entity.PriceAdjustment
import com.bookk.core.data.DecoratorUuidEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.uuid.Uuid

internal class PriceAdjustmentEntity(id: EntityID<Uuid>) : UuidEntity(id) {

    var appointmentId by PriceAdjustmentTable.appointmentId
    val additionalServices by PriceAdjustmentServiceEntity referrersOn PriceAdjustmentServicesTable.priceAdjustmentId
    var priceCurrency by PriceAdjustmentTable.priceCurrency
    var priceUnscaled by PriceAdjustmentTable.priceUnscaled
    var priceScale by PriceAdjustmentTable.priceScale
    var reason by PriceAdjustmentTable.reason

    fun domain(): PriceAdjustment {
        return PriceAdjustment(
            additionalServices = additionalServices.map { it.domain() },
            price = Money.of(
                CurrencyUnit.of(priceCurrency),
                BigDecimal(BigInteger.valueOf(priceUnscaled), priceScale)
            ),
            reason = reason
        )
    }

    companion object : DecoratorUuidEntityClass<PriceAdjustmentEntity>(PriceAdjustmentTable) {
        fun new(ownerId: EntityID<Uuid>, adjustment: PriceAdjustment): PriceAdjustmentEntity = new {
            appointmentId = ownerId
            priceCurrency = adjustment.price.currencyUnit.code
            priceUnscaled = adjustment.price.amount.unscaledValue().longValueExact()
            priceScale = adjustment.price.amount.scale()
            reason = adjustment.reason
        }.apply {
            adjustment.additionalServices.forEach { PriceAdjustmentServiceEntity.new(id, it) }
        }
    }
}
