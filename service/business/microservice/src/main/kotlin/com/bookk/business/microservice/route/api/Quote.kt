package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.service.operation.IssueServiceQuote
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
internal class QuoteRequest(@ProtoNumber(1) val serviceIds: List<Uuid>)

fun Route.quote() {
    authenticate {
        /**
         * Summary: Issue service price quote
         * Description: Issues a quote token for a set of services, returning full service models and a signed quote token
         * Tag: service
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.business.microservice.route.api.QuoteRequest]
         * Response: 200 application/x-protobuf [com.bookk.business.domain.api.service.entity.ServiceQuote] Quote with full service models and token
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Issue quote errors<br>BUSINESS_QUOTE_SERVICE_NOT_FOUND (200013) One or more services not found
         */
        post<Api.Service.Quote> {
            requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<QuoteRequest>()
            val issueQuote by application.injectScoped<IssueServiceQuote>(BusinessScope)

            call.respondWith(
                issueQuote(
                    businessId = it.parent.businessId,
                    serviceIds = body.serviceIds
                )
            )
        }
    }
}
