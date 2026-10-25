package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.ServiceEntity
import com.bookk.business.data.orm.entity.ServiceGroupEntity
import com.bookk.business.data.orm.table.ServiceGroupTable
import com.bookk.business.data.orm.table.ServiceTable
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.entity.ServiceGroup
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.domain.entity.Error
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class ServiceDataSourceImpl : DataSource(), ServiceDataSource {
    override suspend fun createService(service: Service): Service = dbQuery {
        val id = ServiceTable.insertAndGetId {
            it[businessId] = service.businessId
            it[groupId] = service.group.id
            it[name] = service.name
            it[duration] = service.duration.inWholeMinutes.toInt()
            it[priceCurrency] = service.price.currencyUnit.code
            it[priceUnscaled] = service.price.amount.unscaledValue().longValueExact()
            it[priceScale] = service.price.scale
            it[available] = service.isAvailable
        }
        service.copy(id = id.value)
    }

    override suspend fun editService(service: Service): Service = dbQuery {
        val updatedRows = ServiceTable.update(
            where = { (ServiceTable.id eq service.id) and (ServiceTable.businessId eq service.businessId) }
        ) {
            it[groupId] = service.group.id
            it[name] = service.name
            it[duration] = service.duration.inWholeMinutes.toInt()
            it[priceCurrency] = service.price.currencyUnit.code
            it[priceUnscaled] = service.price.amount.unscaledValue().longValueExact()
            it[priceScale] = service.price.scale
            it[available] = service.isAvailable
            it[updatedAt] = Clock.System.now()
        }
        if (updatedRows == 0) throw Error.NotFound()
        service
    }

    override suspend fun getServices(businessId: Uuid): List<Service> = dbQuery {
        ServiceEntity.find {
            ServiceTable.businessId eq businessId
        }.map { it.toDomain() }
    }

    override suspend fun hasServices(businessId: Uuid): Boolean = dbQuery {
        ServiceTable.select(ServiceTable.id)
            .where { ServiceTable.businessId eq businessId }
            .limit(1)
            .empty()
            .not()
    }

    override suspend fun getServicesByIds(ids: List<Uuid>): List<Service> = dbQuery {
        ServiceEntity.find {
            ServiceTable.id inList ids.map { it }
        }.map { it.toDomain() }
    }

    override suspend fun deleteService(id: Uuid, businessId: Uuid) {
        dbQuery { ServiceTable.deleteWhere { (ServiceTable.id eq id) and (ServiceTable.businessId eq businessId) } }
    }

    override suspend fun createServiceGroup(group: ServiceGroup): ServiceGroup = dbQuery {
        val id = ServiceGroupTable.insertAndGetId {
            it[businessId] = group.businessId
            it[name] = group.name
        }
        group.copy(id = id.value)
    }

    override suspend fun deleteServiceGroup(id: Uuid, businessId: Uuid) {
        dbQuery {
            ServiceGroupTable.deleteWhere { (ServiceGroupTable.id eq id) and (ServiceGroupTable.businessId eq businessId) }
        }
    }

    override suspend fun getServiceGroup(id: Uuid, businessId: Uuid): ServiceGroup? = dbQuery {
        ServiceGroupEntity.find {
            (ServiceGroupTable.id eq id) and (ServiceGroupTable.businessId eq businessId)
        }.singleOrNull()?.toDomain()
    }

    override suspend fun getServiceGroups(businessId: Uuid): List<ServiceGroup> = dbQuery {
        ServiceGroupEntity.find {
            ServiceGroupTable.businessId eq businessId
        }.map { it.toDomain() }
    }
}