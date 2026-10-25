package com.bookk.server.business.client.di

import com.bookk.business.domain.api.appointment.operation.GetAppointmentBookingContext
import com.bookk.business.domain.api.appointment.operation.GetAppointmentRescheduleContext
import com.bookk.business.domain.api.business.operation.GetBusinessDetails
import com.bookk.business.domain.api.business.operation.GetBusinessPermission
import com.bookk.business.domain.api.client.operation.GetClientBusinessIds
import com.bookk.business.domain.api.service.operation.GetServicesByIds
import com.bookk.core.AppLevelConstants
import com.bookk.core.AppLevelConstants.SupportedSerializers
import com.bookk.core.client.installInternalCallLogging
import com.bookk.server.business.client.api.BusinessClient
import com.bookk.server.business.client.impl.BusinessClientImpl
import com.bookk.server.business.client.impl.operation.GetAppointmentBookingContextClientImpl
import com.bookk.server.business.client.impl.operation.GetAppointmentRescheduleContextClientImpl
import com.bookk.server.business.client.impl.operation.GetBusinessDetailsClientImpl
import com.bookk.server.business.client.impl.operation.GetBusinessPermissionClientImpl
import com.bookk.server.business.client.impl.operation.GetClientBusinessIdsClientImpl
import com.bookk.server.business.client.impl.operation.GetServicesByIdsClientImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.http.ContentType
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.protobuf.protobuf
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.uuid.Uuid

private const val BUSINESS_HTTP_CLIENT = "businessHttpClient"

@Suppress("KotlinConstantConditions")
fun businessClientModule(qualifier: Qualifier, clientTag: String) = module {
    scope(qualifier) {
        scoped(named(BUSINESS_HTTP_CLIENT)) {
            HttpClient(CIO) {
                install(Resources)
                install(UserAgent) { agent = clientTag }
                install(HttpRequestRetry) {
                    retryOnExceptionOrServerErrors(maxRetries = 3)
                    constantDelay(millis = 50, randomizationMs = 100)
                }
                install(ContentNegotiation) {
                    when (AppLevelConstants.SERIALIZER) {
                        SupportedSerializers.JSON.STR -> {
                            json(Json {
                                prettyPrint = true
                                encodeDefaults = true
                                explicitNulls = false
                            })
                        }

                        SupportedSerializers.PROTOBUF.STR -> {
                            protobuf(ProtoBuf { encodeDefaults = true })
                        }
                    }
                }
                installInternalCallLogging()
                defaultRequest {
                    host = System.getenv("APPLICATION_BUSINESS_SERVICE_HOSTNAME")

                    headers["Idempotency-Key"] = Uuid.random().toString()

                    url { protocol = URLProtocol.HTTP }

                    when (AppLevelConstants.SERIALIZER) {
                        SupportedSerializers.JSON.STR -> contentType(ContentType.Application.Json)
                        SupportedSerializers.PROTOBUF.STR -> contentType(ContentType.Application.ProtoBuf)
                    }
                }
            }
        }
        scoped<GetBusinessDetails> { GetBusinessDetailsClientImpl(get(named(BUSINESS_HTTP_CLIENT))) }
        scoped<GetBusinessPermission> { GetBusinessPermissionClientImpl(get(named(BUSINESS_HTTP_CLIENT))) }
        scoped<GetAppointmentBookingContext> { GetAppointmentBookingContextClientImpl(get(named(BUSINESS_HTTP_CLIENT))) }
        scoped<GetAppointmentRescheduleContext> { GetAppointmentRescheduleContextClientImpl(get(named(BUSINESS_HTTP_CLIENT))) }
        scoped<GetClientBusinessIds> { GetClientBusinessIdsClientImpl(get(named(BUSINESS_HTTP_CLIENT))) }
        scoped<GetServicesByIds> { GetServicesByIdsClientImpl(get(named(BUSINESS_HTTP_CLIENT))) }
        scoped<BusinessClient> { BusinessClientImpl(get(), get(), get(), get(), get(), get()) }
    }
}
