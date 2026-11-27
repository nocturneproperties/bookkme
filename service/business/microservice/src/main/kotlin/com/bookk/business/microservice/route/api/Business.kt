package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.business.entity.BusinessCreateRequest
import com.bookk.business.domain.api.business.entity.BusinessUpdateModel
import com.bookk.business.domain.api.business.operation.CreateBusiness
import com.bookk.business.domain.api.business.operation.GetBusinessById
import com.bookk.business.domain.api.business.operation.GetBusinessesByIds
import com.bookk.business.domain.api.business.operation.GetUserBusinesses
import com.bookk.business.domain.api.business.operation.SetDashboardBusiness
import com.bookk.business.domain.api.business.operation.UpdateBusiness
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
internal class BusinessesByIdsRequest(@ProtoNumber(1) val ids: List<Uuid> = emptyList())

fun Route.businessCrud() {
    authenticate {
        /**
         * Create business
         *
         * Summary: Create business
         * Description: Create new business with specific name. A user can own several businesses, up to their limit (20 by default). A demo account can own one business, which is marked demo and gets an automatic demo- link
         * Tag: business
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.business.domain.api.business.entity.BusinessCreateRequest]
         * Response: 200 application/x-protobuf [com.bookk.business.domain.api.business.entity.Business] The created business
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create business errors<br>BUSINESS_NAME_VALIDATION_ERROR (200002) Business name invalid<br>BUSINESS_INVALID_CURRENCY (200035) Currency code is not a supported ISO 4217 currency<br>BUSINESS_OWNED_BUSINESSES_LIMIT_REACHED (200045) You already own the maximum number of businesses
         * See: docs/operations/business/create-business.md
         */
        post<Api.Business> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<BusinessCreateRequest>()
            val createBusiness by application.injectScoped<CreateBusiness>(BusinessScope)

            call.respondWith(createBusiness(userId = principal.userId, request = body, isDemo = principal.isDemo))
        }
        /**
         * Update business
         *
         * Summary: Update business
         * Description: Partially update business. The currency can only change while the business has no services, sending the stored currency again is always accepted. clientDataRetentionMonths sets how long the business keeps client details on ended appointments and requests before they are anonymized or deleted (12 to 120 months, default 36)
         * Tag: business
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.business.domain.api.business.entity.BusinessUpdateModel] Non-null fields will be updated
         * Response: 204 application/x-protobuf No content
         * Response: 400 application/x-protobuf Path id does not match body id, or the working schedule does not cover all 7 days
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] User is not allowed to update the business
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Update business errors<br>BUSINESS_ACTIVE_DAY_WITHOUT_WORK_HOURS (200019) Active day must have at least one work hour<br>BUSINESS_EMPTY_WORK_HOUR_RANGE (200042) Working range must not start and end at the same time<br>BUSINESS_INVALID_DAY_OFF_RANGE (200020) Day off range start date must not be after end date<br>BUSINESS_INVALID_CURRENCY (200035) Currency code is not a supported ISO 4217 currency<br>BUSINESS_CURRENCY_LOCKED (200036) Currency cannot be changed once the business has services<br>BUSINESS_CLIENT_DATA_RETENTION_OUT_OF_RANGE (200041) Client data retention must be between 12 and 120 months
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/business/update-business.md
         */
        put<Api.Business.Id> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<BusinessUpdateModel>()
            val updateBusiness by application.injectScoped<UpdateBusiness>(BusinessScope)

            if (it.id != body.id) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request")
            } else {
                call.respondWith(updateBusiness(requestUserId = principal.userId, businessUpdateModel = body))
            }
        }
        /**
         * Get caller's businesses
         *
         * Summary: Get caller's businesses
         * Description: Get every business the caller owns or is an active employee of, with the caller's permissions, employee id and client id in each, plus the dashboard business id. Dashboard business is a business that user selected to be displayed as main business
         * Tag: business
         * Security: jwt
         * Response: 200 application/x-protobuf [com.bookk.business.domain.api.business.entity.UserBusinesses] User business info
         */
        get<Api.Business.Mine> { path ->
            val getUserBusinesses by application.injectScoped<GetUserBusinesses>(BusinessScope)
            val principal = requireNotNull(call.principal<AppPrincipal>())

            call.respondWith(getUserBusinesses(userId = principal.userId))
        }
        /**
         * Get businesses by ids
         *
         * Summary: Get businesses by ids
         * Description: Get the info of up to 100 businesses at once, each with the caller's permissions, employee id and client id in it, like the single business read. Ids with no business are left out, a repeated id is returned once, and businesses keep the order of their ids in the request
         * Tag: business
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.business.microservice.route.api.BusinessesByIdsRequest]
         * Response: 200 application/protobuf [com.bookk.business.domain.api.business.entity.Business]+ Businesses found
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Get businesses by ids errors<br>BUSINESS_BULK_READ_TOO_LARGE (200054) At most 100 businesses can be read at once
         */
        post<Api.Business.Bulk> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<BusinessesByIdsRequest>()
            val getBusinessesByIds by application.injectScoped<GetBusinessesByIds>(BusinessScope)

            call.respondWith(getBusinessesByIds(ids = body.ids, requestingUserId = principal.userId))
        }
        /**
         * Get business by id
         *
         * Summary: Get business by id
         * Description: Get business info with the caller's permissions, employee id and client id in it. Employee id is empty while the caller's employee access is suspended
         * Tag: business
         * Security: jwt
         * Response: 200 application/x-protobuf [com.bookk.business.domain.api.business.entity.Business] Business info
         */
        get<Api.Business.Id> { path ->
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getBusinessById by application.injectScoped<GetBusinessById>(BusinessScope)

            call.respondWith(getBusinessById(id = path.id, requestingUserId = principal.userId))
        }
        /**
         * Set dashboard business
         *
         * Summary: Set dashboard business
         * Description: Select which of the caller's businesses is displayed as the dashboard business
         * Tag: business
         * Security: jwt
         * Response: 204 application/x-protobuf No content
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] User is not allowed to select this business as their dashboard business
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/business/set-dashboard-business.md
         */
        put<Api.Business.Id.Dashboard> { path ->
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val setDashboardBusiness by application.injectScoped<SetDashboardBusiness>(BusinessScope)

            call.respondWith(setDashboardBusiness(userId = principal.userId, businessId = path.parent.id))
        }
    }
}
