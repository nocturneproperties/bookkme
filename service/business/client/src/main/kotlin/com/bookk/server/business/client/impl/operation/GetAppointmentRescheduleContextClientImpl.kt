package com.bookk.server.business.client.impl.operation

import com.bookk.business.domain.api.appointment.entity.AppointmentRescheduleContext
import com.bookk.business.domain.api.appointment.entity.AppointmentRescheduleContextRequest
import com.bookk.business.domain.api.appointment.operation.GetAppointmentRescheduleContext
import com.bookk.core.client.bodyOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.business.client.impl.BusinessRouting
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import kotlin.uuid.Uuid

internal class GetAppointmentRescheduleContextClientImpl(
    private val httpClient: HttpClient
) : GetAppointmentRescheduleContext {

    override suspend fun invoke(
        businessId: Uuid,
        employeeId: Uuid?,
        serviceIds: List<Uuid>
    ): Result<AppointmentRescheduleContext> = runSuspendCatching {
        httpClient.post(
            BusinessRouting.Api.Internal.Business.Id.AppointmentRescheduleContext(
                parent = BusinessRouting.Api.Internal.Business.Id(id = businessId)
            )
        ) {
            setBody(AppointmentRescheduleContextRequest(employeeId = employeeId, serviceIds = serviceIds))
        }.bodyOrThrow()
    }
}
