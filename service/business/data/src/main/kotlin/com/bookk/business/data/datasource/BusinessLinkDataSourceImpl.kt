package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.BusinessLinkChangeEntity
import com.bookk.business.data.orm.entity.BusinessLinkEntity
import com.bookk.business.data.orm.table.BusinessLinkChangeTable
import com.bookk.business.data.orm.table.BusinessLinkTable
import com.bookk.business.domain.api.business.entity.BusinessLink
import com.bookk.business.domain.datasource.BusinessLinkDataSource
import com.bookk.business.domain.datasource.StoredBusinessLink
import com.bookk.core.data.DataSource
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class BusinessLinkDataSourceImpl : DataSource(), BusinessLinkDataSource {

    override suspend fun getLinkByName(name: String): StoredBusinessLink? = dbQuery {
        BusinessLinkEntity.findByName(name)?.toStored()
    }

    override suspend fun getLinkByNameForUpdate(name: String): StoredBusinessLink? = dbQuery {
        BusinessLinkEntity.findByNameForUpdate(name)?.toStored()
    }

    override suspend fun getCurrentLink(businessId: Uuid): BusinessLink? = dbQuery {
        BusinessLinkEntity.findCurrent(businessId)?.toDomain()
    }

    override suspend fun createLink(businessId: Uuid, name: String) {
        dbQuery { BusinessLinkEntity.new(businessId, name) }
    }

    override suspend fun releaseCurrentLink(businessId: Uuid, releasedAt: Instant) {
        dbQuery { BusinessLinkEntity.findCurrentAndRelease(businessId, releasedAt) }
    }

    override suspend fun restoreLink(businessId: Uuid, name: String) {
        dbQuery { BusinessLinkEntity.findAndRestore(businessId, name) }
    }

    override suspend fun deleteLink(name: String) {
        dbQuery { BusinessLinkTable.deleteWhere { BusinessLinkTable.name eq name } }
    }

    override suspend fun deleteLinksReleasedBefore(before: Instant) {
        dbQuery { BusinessLinkTable.deleteWhere { BusinessLinkTable.releasedAt less before } }
    }

    override suspend fun recordLinkChange(businessId: Uuid, changedAt: Instant) {
        dbQuery { BusinessLinkChangeEntity.new(businessId, changedAt) }
    }

    override suspend fun countLinkChangesSince(businessId: Uuid, since: Instant): Long = dbQuery {
        BusinessLinkChangeEntity.count(
            (BusinessLinkChangeTable.businessId eq businessId) and (BusinessLinkChangeTable.changedAt greaterEq since),
        )
    }

    override suspend fun deleteLinkChangesBefore(before: Instant) {
        dbQuery { BusinessLinkChangeTable.deleteWhere { BusinessLinkChangeTable.changedAt less before } }
    }
}
