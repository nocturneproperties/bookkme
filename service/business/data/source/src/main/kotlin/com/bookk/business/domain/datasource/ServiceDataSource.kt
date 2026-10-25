package com.bookk.business.domain.datasource

import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.entity.ServiceGroup
import kotlin.uuid.Uuid

interface ServiceDataSource {
    suspend fun createService(service: Service): Service
    suspend fun editService(service: Service): Service
    suspend fun getServices(businessId: Uuid): List<Service>
    suspend fun hasServices(businessId: Uuid): Boolean
    suspend fun getServicesByIds(ids: List<Uuid>): List<Service>
    suspend fun deleteService(id: Uuid, businessId: Uuid)
    suspend fun createServiceGroup(group: ServiceGroup): ServiceGroup
    suspend fun deleteServiceGroup(id: Uuid, businessId: Uuid)
    suspend fun getServiceGroup(id: Uuid, businessId: Uuid): ServiceGroup?
    suspend fun getServiceGroups(businessId: Uuid): List<ServiceGroup>
}