package com.bookk.business.domain.datasource

import com.bookk.business.domain.api.business.entity.BusinessLink
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class StoredBusinessLink(val businessId: Uuid, val name: String, val releasedAt: Instant?) {
    val isCurrent: Boolean get() = releasedAt == null

    fun link(): BusinessLink {
        return BusinessLink(businessId = businessId, name = name)
    }

    fun isReleasedBefore(instant: Instant): Boolean {
        val released = releasedAt ?: return false
        return released < instant
    }

    companion object {
        fun stub(
            businessId: Uuid = Uuid.random(),
            name: String = "business-link",
            releasedAt: Instant? = null,
        ): StoredBusinessLink {
            return StoredBusinessLink(businessId = businessId, name = name, releasedAt = releasedAt)
        }
    }
}
