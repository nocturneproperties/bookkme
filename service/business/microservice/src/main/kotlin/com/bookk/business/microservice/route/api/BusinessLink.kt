package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.business.operation.GetBusinessLink
import com.bookk.business.domain.api.business.operation.ResolveBusinessLink
import com.bookk.business.domain.api.business.operation.SetBusinessLink
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.put
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
internal class BusinessLinkRequest(
    @ProtoNumber(1) val name: String,
)

fun Route.businessLink() {
    /**
     * Resolve business link
     *
     * Summary: Resolve business link
     * Description: Find the business behind a link name, for opening bookkme.com/{name}. Needs no sign-in, and nginx allows 30 lookups a minute per address. Letter case and surrounding spaces are ignored. A name the business used before its last renames still finds it for 90 days, and the response then carries the business's current name
     * Tag: business
     * Response: 200 application/x-protobuf [com.bookk.business.domain.api.business.entity.BusinessLink] The business id and its current link name
     * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Resolve business link errors<br>BUSINESS_LINK_NOT_FOUND (200053) No business has this link
     * Response: 429 Too many lookups from this address
     */
    get<Api.Business.LinkName> { path ->
        val resolveBusinessLink by application.injectScoped<ResolveBusinessLink>(BusinessScope)

        call.respondWith(resolveBusinessLink(path.name))
    }
    authenticate {
        /**
         * Get business link
         *
         * Summary: Get business link
         * Description: The business's current link name, for showing and sharing bookkme.com/{name}. Anyone who can view the business may read it
         * Tag: business
         * Security: jwt
         * Response: 200 application/x-protobuf [com.bookk.business.domain.api.business.entity.BusinessLink] The business's current link
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] The business has no link, or the caller cannot view the business<br>BUSINESS_LINK_NOT_FOUND (200053) The business has no link
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         */
        get<Api.Business.Id.Link> { path ->
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getBusinessLink by application.injectScoped<GetBusinessLink>(BusinessScope)

            call.respondWith(getBusinessLink(userId = principal.userId, businessId = path.parent.id))
        }
        /**
         * Set business link
         *
         * Summary: Set business link
         * Description: Set or change the business's link name, so clients can open bookkme.com/{name}. Only the owner may do it. The name is stored in lowercase and must be 3 to 32 latin letters or digits, with single hyphens between them. A replaced name stays held by the business for 90 days, so no other business can take it, and setting it again makes it current. The link can change at most 3 times within 90 days, switching back to a held name included. Setting the current name again changes nothing
         * Tag: business
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.business.microservice.route.api.BusinessLinkRequest]
         * Response: 200 application/x-protobuf [com.bookk.business.domain.api.business.entity.BusinessLink] The business's link
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] The business does not exist or the caller is not its owner
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Set business link errors<br>BUSINESS_LINK_INVALID (200049) Link name must be 3 to 32 latin letters, digits or single hyphens between them<br>BUSINESS_LINK_RESERVED (200050) This link name is reserved<br>BUSINESS_LINK_TAKEN (200051) This link name is already taken<br>BUSINESS_LINK_CHANGE_LIMIT_REACHED (200052) The link was changed too often, try again later
         * See: docs/operations/business/set-business-link.md
         */
        put<Api.Business.Id.Link> { path ->
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<BusinessLinkRequest>()
            val setBusinessLink by application.injectScoped<SetBusinessLink>(BusinessScope)

            call.respondWith(setBusinessLink(userId = principal.userId, businessId = path.parent.id, name = body.name))
        }
    }
}
