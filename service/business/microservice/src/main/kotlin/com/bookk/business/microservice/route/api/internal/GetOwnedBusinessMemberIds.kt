package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.employee.operation.GetOwnedBusinessMemberIds
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.getOwnedBusinessMemberIds() {
    /**
     * Get members of an owner's business
     *
     * Summary: Get members of an owner's business
     * Description: User ids of every employee of the business the given user owns, the owner excluded. Empty when the user owns no business. Used by the authorization service to decide whether an inactive owner's business is still in use
     * Tag: internal
     * Response: 200 application/protobuf [kotlin.uuid.Uuid]+ Member user ids
     */
    get<Api.Internal.Owner.Members> { members ->
        val getOwnedBusinessMemberIds by application.injectScoped<GetOwnedBusinessMemberIds>(BusinessScope)
        call.respondWith(getOwnedBusinessMemberIds(members.userId))
    }
}
