package com.bookk.business.domain.impl.operation

import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.datasource.ServiceDataSource
import kotlin.uuid.Uuid

internal suspend fun ServiceDataSource.getServicesExpanded(
    businessId: Uuid,
    serviceIds: List<Uuid>
): List<Service>? {
    val distinctIds = serviceIds.toSet()
    val foundServices = getServicesByIds(distinctIds.toList())

    val isSizeNotMatched = foundServices.size != distinctIds.size
    val isNotBelongToBusiness = foundServices.any { it.businessId != businessId }
    if (isSizeNotMatched || isNotBelongToBusiness) return null

    val servicesById = foundServices.associateBy { it.id }
    return serviceIds.map { servicesById.getValue(it) }
}
