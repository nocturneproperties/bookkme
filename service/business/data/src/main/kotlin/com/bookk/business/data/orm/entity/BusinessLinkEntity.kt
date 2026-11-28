package com.bookk.business.data.orm.entity

import com.bookk.business.data.orm.table.BusinessLinkTable
import com.bookk.business.data.orm.table.BusinessTable
import com.bookk.business.domain.api.business.entity.BusinessLink
import com.bookk.business.domain.datasource.StoredBusinessLink
import com.bookk.core.data.DecoratorUuidEntityClass
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class BusinessLinkEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var businessId by BusinessLinkTable.businessId
    var name by BusinessLinkTable.name
    var releasedAt by BusinessLinkTable.releasedAt

    fun toStored(): StoredBusinessLink {
        return StoredBusinessLink(businessId = businessId.value, name = name, releasedAt = releasedAt)
    }

    fun toDomain(): BusinessLink {
        return toStored().link()
    }

    companion object : DecoratorUuidEntityClass<BusinessLinkEntity>(BusinessLinkTable) {
        fun new(businessId: Uuid, name: String): BusinessLinkEntity {
            return new {
                this.businessId = EntityID(businessId, BusinessTable)
                this.name = name
            }
        }

        fun findByName(name: String): BusinessLinkEntity? {
            return find { BusinessLinkTable.name eq name }.firstOrNull()
        }

        fun findByNameForUpdate(name: String): BusinessLinkEntity? {
            return find { BusinessLinkTable.name eq name }.forUpdate().firstOrNull()
        }

        fun findCurrent(businessId: Uuid): BusinessLinkEntity? {
            return find { (BusinessLinkTable.businessId eq businessId) and BusinessLinkTable.releasedAt.isNull() }
                .firstOrNull()
        }

        fun findCurrentAndRelease(businessId: Uuid, releasedAt: Instant) {
            findCurrent(businessId)?.releasedAt = releasedAt
        }

        fun findAndRestore(businessId: Uuid, name: String) {
            find { (BusinessLinkTable.businessId eq businessId) and (BusinessLinkTable.name eq name) }
                .firstOrNull()
                ?.releasedAt = null
        }
    }
}
