package com.bookk.business.data.orm.entity

import com.bookk.business.data.orm.table.BusinessDashboardTable
import com.bookk.core.data.DecoratorUuidEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.uuid.Uuid

internal class DashboardBusinessEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    val userId by BusinessDashboardTable.businessId
    val business by BusinessEntity referencedOn BusinessDashboardTable.businessId
    val createdAt by BusinessDashboardTable.createdAt
    val updatedAt by BusinessDashboardTable.updatedAt

    companion object : DecoratorUuidEntityClass<DashboardBusinessEntity>(BusinessDashboardTable)
}