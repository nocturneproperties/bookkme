package com.bookk.business.domain.datasource

import com.bookk.business.domain.api.export.BusinessClientExport
import com.bookk.business.domain.api.export.PendingInvitationExport
import kotlin.uuid.Uuid

interface BusinessDataExportDataSource {
    suspend fun getClients(businessId: Uuid): List<BusinessClientExport>
    suspend fun getPendingInvitations(businessId: Uuid): List<PendingInvitationExport>
}
