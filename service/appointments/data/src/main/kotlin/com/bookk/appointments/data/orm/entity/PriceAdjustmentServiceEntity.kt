package com.bookk.appointments.data.orm.entity

import com.bookk.appointments.data.orm.table.PriceAdjustmentServicesTable
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.core.data.DecoratorUuidEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal class PriceAdjustmentServiceEntity(id: EntityID<Uuid>) : UuidEntity(id) {

    var priceAdjustmentId by PriceAdjustmentServicesTable.priceAdjustmentId
    var serviceId by PriceAdjustmentServicesTable.serviceId
    var serviceName by PriceAdjustmentServicesTable.serviceName
    var serviceGroupId by PriceAdjustmentServicesTable.serviceGroupId
    var priceCurrency by PriceAdjustmentServicesTable.priceCurrency
    var priceUnscaled by PriceAdjustmentServicesTable.priceUnscaled
    var priceScale by PriceAdjustmentServicesTable.priceScale
    var durationMinutes by PriceAdjustmentServicesTable.durationMinutes

    fun domain(): ServiceSnapshot {
        return ServiceSnapshot(
            id = serviceId,
            name = serviceName,
            groupId = serviceGroupId,
            price = Money.of(
                CurrencyUnit.of(priceCurrency),
                BigDecimal(BigInteger.valueOf(priceUnscaled), priceScale)
            ),
            duration = durationMinutes.minutes
        )
    }

    companion object : DecoratorUuidEntityClass<PriceAdjustmentServiceEntity>(PriceAdjustmentServicesTable) {
        fun new(ownerId: EntityID<Uuid>, service: ServiceSnapshot): PriceAdjustmentServiceEntity = new {
            priceAdjustmentId = ownerId
            serviceId = service.id
            serviceName = service.name
            serviceGroupId = service.groupId
            priceCurrency = service.price.currencyUnit.code
            priceUnscaled = service.price.amount.unscaledValue().longValueExact()
            priceScale = service.price.amount.scale()
            durationMinutes = service.duration.inWholeMinutes
        }
    }
}
