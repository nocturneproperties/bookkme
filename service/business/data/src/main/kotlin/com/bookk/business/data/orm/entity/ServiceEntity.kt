package com.bookk.business.data.orm.entity

import com.bookk.business.data.orm.table.ServiceTable
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.core.data.DecoratorUuidEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal class ServiceEntity(id: EntityID<Uuid>) : UuidEntity(id) {

    var businessId by ServiceTable.businessId
    var group by ServiceGroupEntity referencedOn ServiceTable.groupId
    var duration by ServiceTable.duration
    var name by ServiceTable.name
    var priceCurrency by ServiceTable.priceCurrency
    var priceUnscaled by ServiceTable.priceUnscaled
    var priceScale by ServiceTable.priceScale
    var available by ServiceTable.available
    var createdAt by ServiceTable.createdAt
    var updatedAt by ServiceTable.updatedAt

    companion object : DecoratorUuidEntityClass<ServiceEntity>(ServiceTable)

    fun toDomain(): Service {
        return Service(
            id = id.value,
            businessId = businessId.value,
            group = group.toDomain(),
            duration = duration.minutes,
            name = name,
            price = Money.of(
                CurrencyUnit.of(priceCurrency),
                BigDecimal(BigInteger.valueOf(priceUnscaled), priceScale)
            ),
            isAvailable = available,
            createdAt = createdAt
        )
    }
}