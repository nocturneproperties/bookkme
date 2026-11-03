package com.bookk.server.business.client.impl

import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext
import com.bookk.business.domain.api.appointment.entity.AppointmentRescheduleContext
import com.bookk.business.domain.api.appointment.operation.GetAppointmentBookingContext
import com.bookk.business.domain.api.appointment.operation.GetAppointmentRescheduleContext
import com.bookk.business.domain.api.appointment.operation.GetStaffBookingContext
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.business.operation.GetBusinessDetails
import com.bookk.business.domain.api.business.operation.GetBusinessPermission
import com.bookk.business.domain.api.client.operation.GetClientBusinessIds
import com.bookk.business.domain.api.employee.operation.GetOwnedBusinessMemberIds
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.operation.GetServicesByIds
import com.bookk.server.business.client.api.BusinessClient
import com.bookk.server.business.client.api.BusinessDTO
import library.permissions.ResourcePermission
import kotlin.uuid.Uuid

internal class BusinessClientImpl(
    private val getBusinessDetails: GetBusinessDetails,
    private val getBusinessPermission: GetBusinessPermission,
    private val getAppointmentBookingContext: GetAppointmentBookingContext,
    private val getAppointmentRescheduleContext: GetAppointmentRescheduleContext,
    private val getClientBusinessIds: GetClientBusinessIds,
    private val getServicesByIds: GetServicesByIds,
    private val getStaffBookingContext: GetStaffBookingContext,
    private val getOwnedBusinessMemberIds: GetOwnedBusinessMemberIds,
) : BusinessClient {
    override suspend fun getBusinessById(id: Uuid): Result<BusinessDTO> = getBusinessDetails.invoke(id).map(BusinessDTO::from)

    override suspend fun getPermission(userId: Uuid, businessId: Uuid, resource: BusinessResource): Result<ResourcePermission> = getBusinessPermission.invoke(userId, businessId, resource)

    override suspend fun getAppointmentBookingContext(
        businessId: Uuid,
        employeeId: Uuid,
        userId: Uuid,
        serviceIds: List<Uuid>,
    ): Result<AppointmentBookingContext> = getAppointmentBookingContext.invoke(businessId, employeeId, userId, serviceIds)

    override suspend fun getStaffBookingContext(
        businessId: Uuid,
        employeeId: Uuid,
        clientId: Uuid,
        serviceIds: List<Uuid>,
    ): Result<AppointmentBookingContext> = getStaffBookingContext.invoke(businessId, employeeId, clientId, serviceIds)

    override suspend fun getAppointmentRescheduleContext(
        businessId: Uuid,
        employeeId: Uuid?,
        serviceIds: List<Uuid>,
    ): Result<AppointmentRescheduleContext> = getAppointmentRescheduleContext.invoke(businessId, employeeId, serviceIds)

    override suspend fun getClientBusinessIds(userId: Uuid): Result<List<Uuid>> = getClientBusinessIds.invoke(userId)

    override suspend fun getServicesByIds(businessId: Uuid, serviceIds: List<Uuid>): Result<List<Service>> = getServicesByIds.invoke(businessId, serviceIds)

    override suspend fun getOwnedBusinessMemberIds(ownerUserId: Uuid): Result<List<Uuid>> = getOwnedBusinessMemberIds.invoke(ownerUserId)
}
