package com.bookk.business.data.orm.entity

import com.bookk.business.data.orm.table.BusinessLinkChangeTable
import com.bookk.business.data.orm.table.BusinessTable
import com.bookk.core.data.DecoratorUuidEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class BusinessLinkChangeEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var businessId by BusinessLinkChangeTable.businessId
    var changedAt by BusinessLinkChangeTable.changedAt

    companion object : DecoratorUuidEntityClass<BusinessLinkChangeEntity>(BusinessLinkChangeTable) {
        fun new(businessId: Uuid, changedAt: Instant): BusinessLinkChangeEntity {
            return new {
                this.businessId = EntityID(businessId, BusinessTable)
                this.changedAt = changedAt
            }
        }
    }
}
