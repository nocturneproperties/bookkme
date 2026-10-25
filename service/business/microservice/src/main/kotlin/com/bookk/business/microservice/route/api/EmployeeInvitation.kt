package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.employee.entity.EmployeeInvitation
import com.bookk.business.domain.api.employee.operation.CreateEmployeeInvitation
import com.bookk.business.domain.api.employee.operation.GetEmployeeInvitations
import com.bookk.business.domain.api.employee.operation.JoinBusiness
import com.bookk.business.domain.api.employee.operation.RevokeEmployeeInvitation
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.openapi.describe
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
internal class EmployeeInvitationRedeemRequest(
    @ProtoNumber(1) val code: String
)

fun Route.employeeInvitationCrud() {
    authenticate {
        /**
         * Summary: Create employee invitation
         * Description: Generates a new invite code the business owner can share with a future employee
         * Tag: employee
         * Security: jwt
         * Response: 200 application/x-protobuf [com.bookk.business.domain.api.employee.entity.EmployeeInvitation] Created invitation
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Business is not found or the caller has no rights to invite
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create invitation errors<br>BUSINESS_EMPLOYEE_PENDING_INVITATIONS_LIMIT_REACHED (200028) Business already has the maximum number of pending invitations<br>BUSINESS_EMPLOYEE_DAILY_INVITATIONS_LIMIT_REACHED (200029) Business already created the maximum number of invitations in the last 24 hours
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/business/create-employee-invitation.md
         */
        post<Api.EmployeeInvitation> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val createEmployeeInvitation by application.injectScoped<CreateEmployeeInvitation>(BusinessScope)

            call.respondWith(createEmployeeInvitation(requestUserId = principal.userId, businessId = it.businessId))
        }

        /**
         * Summary: Get employee invitations
         * Description: Returns all invitations in this business sent by the calling user, regardless of status
         * Tag: employee
         * Security: jwt
         */
        get<Api.EmployeeInvitation> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getEmployeeInvitations by application.injectScoped<GetEmployeeInvitations>(BusinessScope)

            call.respondWith(getEmployeeInvitations(userId = principal.userId, businessId = it.businessId))
        }.describe {
            responses {
                response(HttpStatusCode.OK.value) {
                    schema = jsonSchema<List<EmployeeInvitation>>()
                    description = "Invitations sent by the calling user"
                    ContentType.Application.ProtoBuf()
                }
            }
        }

        /**
         * Summary: Join business
         * Description: Joins the business as an employee using an invite code shared by the owner
         * Tag: employee
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.business.microservice.route.api.EmployeeInvitationRedeemRequest]
         * Response: 200 application/x-protobuf [com.bookk.business.domain.api.employee.entity.Employee] Created employee
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Invite code is unknown
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Join business errors<br>BUSINESS_EMPLOYEE_INVITATION_CODE_EMPTY (200031) Invitation code must not be empty<br>BUSINESS_EMPLOYEE_INVITATION_ALREADY_PROCESSED (200017) Invitation is already processed<br>BUSINESS_EMPLOYEE_EXISTS (200018) User is already an employee of this business
         * See: docs/operations/business/join-business.md
         */
        post<Api.RedeemEmployeeInvitation> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<EmployeeInvitationRedeemRequest>()
            val joinBusiness by application.injectScoped<JoinBusiness>(BusinessScope)

            call.respondWith(joinBusiness(requestUserId = principal.userId, code = body.code))
        }

        /**
         * Summary: Revoke employee invitation
         * Description: Lets the business owner manually revoke a pending invitation before it is redeemed or expires
         * Tag: employee
         * Security: jwt
         * Response: 204 application/x-protobuf Invitation revoked
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Invitation is not found or the caller has no rights to revoke it
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Revoke invitation errors<br>BUSINESS_EMPLOYEE_INVITATION_ALREADY_PROCESSED (200017) Invitation is already processed
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/business/revoke-employee-invitation.md
         */
        post<Api.EmployeeInvitation.Revoke> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val revokeEmployeeInvitation by application.injectScoped<RevokeEmployeeInvitation>(BusinessScope)

            call.respondWith(
                revokeEmployeeInvitation(
                    requestUserId = principal.userId,
                    businessId = it.parent.businessId,
                    id = it.id
                )
            )
        }
    }
}
