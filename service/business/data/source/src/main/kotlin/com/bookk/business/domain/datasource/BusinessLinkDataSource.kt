package com.bookk.business.domain.datasource

import com.bookk.business.domain.api.business.entity.BusinessLink
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface BusinessLinkDataSource {
    suspend fun getLinkByName(name: String): StoredBusinessLink?
    suspend fun getLinkByNameForUpdate(name: String): StoredBusinessLink?
    suspend fun getCurrentLink(businessId: Uuid): BusinessLink?
    suspend fun createLink(businessId: Uuid, name: String)
    suspend fun releaseCurrentLink(businessId: Uuid, releasedAt: Instant)
    suspend fun restoreLink(businessId: Uuid, name: String)
    suspend fun deleteLink(name: String)
    suspend fun deleteLinksReleasedBefore(before: Instant)
    suspend fun recordLinkChange(businessId: Uuid, changedAt: Instant)
    suspend fun countLinkChangesSince(businessId: Uuid, since: Instant): Long
    suspend fun deleteLinkChangesBefore(before: Instant)
}
