package com.bookk.business.data.orm.entity

import com.bookk.business.data.orm.table.ServiceGroupTable
import com.bookk.business.domain.api.service.entity.ServiceGroup
import com.bookk.core.data.DecoratorUuidEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.uuid.Uuid

internal class ServiceGroupEntity(id: EntityID<Uuid>) : UuidEntity(id) {

    val businessId by ServiceGroupTable.businessId
    val name by ServiceGroupTable.name
    val createdAt by ServiceGroupTable.createdAt
    val updatedAt by ServiceGroupTable.updatedAt

    companion object : DecoratorUuidEntityClass<ServiceGroupEntity>(ServiceGroupTable)

    fun toDomain(): ServiceGroup {
        return ServiceGroup(
            id = id.value,
            businessId = businessId.value,
            name = name,
            createdAt = createdAt
        )
    }
}