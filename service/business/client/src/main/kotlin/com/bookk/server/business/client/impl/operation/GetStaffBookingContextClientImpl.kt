package com.bookk.server.business.client.impl.operation

import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext
import com.bookk.business.domain.api.appointment.entity.StaffBookingContextRequest
import com.bookk.business.domain.api.appointment.operation.GetStaffBookingContext
import com.bookk.core.client.bodyOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.business.client.impl.BusinessRouting
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import kotlin.uuid.Uuid

internal class GetStaffBookingContextClientImpl(
    private val httpClient: HttpClient,
) : GetStaffBookingContext {

    override suspend fun invoke(
        businessId: Uuid,
        employeeId: Uuid,
        clientId: Uuid,
        serviceIds: List<Uuid>,
    ): Result<AppointmentBookingContext> = runSuspendCatching {
        httpClient.post(
            BusinessRouting.Api.Internal.Business.Id.StaffBookingContext(
                parent = BusinessRouting.Api.Internal.Business.Id(id = businessId),
            ),
        ) {
            setBody(StaffBookingContextRequest(employeeId = employeeId, clientId = clientId, serviceIds = serviceIds))
        }.bodyOrThrow()
    }
}
