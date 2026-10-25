package com.bookk.appointments.data.orm.entity

import com.bookk.appointments.data.orm.table.AppointmentRequestServicesTable
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

internal class AppointmentRequestServiceEntity(id: EntityID<Uuid>) : UuidEntity(id) {

    var requestId by AppointmentRequestServicesTable.requestId
    var serviceId by AppointmentRequestServicesTable.serviceId
    var serviceName by AppointmentRequestServicesTable.serviceName
    var serviceGroupId by AppointmentRequestServicesTable.serviceGroupId
    var priceCurrency by AppointmentRequestServicesTable.priceCurrency
    var priceUnscaled by AppointmentRequestServicesTable.priceUnscaled
    var priceScale by AppointmentRequestServicesTable.priceScale
    var durationMinutes by AppointmentRequestServicesTable.durationMinutes

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

    companion object : DecoratorUuidEntityClass<AppointmentRequestServiceEntity>(AppointmentRequestServicesTable) {
        fun new(ownerId: EntityID<Uuid>, service: ServiceSnapshot): AppointmentRequestServiceEntity = new {
            requestId = ownerId
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