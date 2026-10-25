package com.bookk.appointments.data.orm.entity

import com.bookk.appointments.data.orm.table.AppointmentServicesTable
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

internal class AppointmentServiceEntity(id: EntityID<Uuid>) : UuidEntity(id) {

    var appointmentId by AppointmentServicesTable.appointmentId
    var serviceId by AppointmentServicesTable.serviceId
    var serviceName by AppointmentServicesTable.serviceName
    var serviceGroupId by AppointmentServicesTable.serviceGroupId
    var priceCurrency by AppointmentServicesTable.priceCurrency
    var priceUnscaled by AppointmentServicesTable.priceUnscaled
    var priceScale by AppointmentServicesTable.priceScale
    var durationMinutes by AppointmentServicesTable.durationMinutes

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

    companion object : DecoratorUuidEntityClass<AppointmentServiceEntity>(AppointmentServicesTable) {
        fun new(ownerId: EntityID<Uuid>, service: ServiceSnapshot): AppointmentServiceEntity = new {
            appointmentId = ownerId
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