package com.bookk.server.business.client.impl.operation

import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContext
import com.bookk.business.domain.api.appointment.entity.AppointmentBookingContextRequest
import com.bookk.business.domain.api.appointment.operation.GetAppointmentBookingContext
import com.bookk.core.client.bodyOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.business.client.impl.BusinessRouting
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import kotlin.uuid.Uuid

internal class GetAppointmentBookingContextClientImpl(
    private val httpClient: HttpClient
) : GetAppointmentBookingContext {

    override suspend fun invoke(
        businessId: Uuid,
        employeeId: Uuid,
        userId: Uuid,
        serviceIds: List<Uuid>
    ): Result<AppointmentBookingContext> = runSuspendCatching {
        httpClient.post(
            BusinessRouting.Api.Internal.Business.Id.AppointmentBookingContext(
                parent = BusinessRouting.Api.Internal.Business.Id(id = businessId)
            )
        ) {
            setBody(AppointmentBookingContextRequest(employeeId = employeeId, userId = userId, serviceIds = serviceIds))
        }.bodyOrThrow()
    }
}
