package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.employee.operation.GetOwnedBusinessMemberIds
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.openapi.describe
import kotlin.uuid.Uuid

internal fun Route.getOwnedBusinessMemberIds() {
    /**
     * Summary: Get members of an owner's business
     * Description: User ids of every employee of the business the given user owns, the owner excluded. Empty when the user owns no business. Used by the authorization service to decide whether an inactive owner's business is still in use
     * Tag: internal
     */
    get<Api.Internal.Owner.Members> { members ->
        val getOwnedBusinessMemberIds by application.injectScoped<GetOwnedBusinessMemberIds>(BusinessScope)
        call.respondWith(getOwnedBusinessMemberIds(members.userId))
    }.describe {
        responses {
            response(HttpStatusCode.OK.value) {
                schema = jsonSchema<List<Uuid>>()
                description = "Member user ids"
                ContentType.Application.ProtoBuf()
            }
        }
    }
}
