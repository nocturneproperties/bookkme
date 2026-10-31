package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientRemote
import com.bookk.business.domain.api.client.entity.ClientUpdateModel
import com.bookk.business.domain.api.client.entity.toDetachedClient
import com.bookk.business.domain.api.client.operation.CreateClient
import com.bookk.business.domain.api.client.operation.DeleteClient
import com.bookk.business.domain.api.client.operation.GetClients
import com.bookk.business.domain.api.client.operation.ImportClients
import com.bookk.business.domain.api.client.operation.UpdateClient
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.BusinessRouting.Api
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.patch
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.openapi.describe
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
internal class ClientImportRequest(
    @ProtoNumber(1) val clients: List<ClientImportEntry>
)

@Serializable
internal class ClientImportEntry(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val lastName: String,
    @ProtoNumber(3) val phone: String?,
    @ProtoNumber(4) val email: String?,
    @ProtoNumber(5) val description: String?
) {
    fun toDetachedClient() = Client.Detached(
        id = Uuid.random(),
        name = name,
        lastName = lastName,
        phone = phone,
        email = email,
        description = description
    )
}

fun Route.clientCrud() {
    authenticate {
        /**
         * Summary: Import clients
         * Description: Create up to 500 detached clients at once, e.g. from the phone book. Valid clients are created, invalid ones or ones whose phone or email already belongs to a client of the business (or to an earlier client of the same import) are skipped and returned in rejected with their position in the request and error code<br>BUSINESS_CLIENT_NAME_VALIDATION_ERROR (200005) Client name, last name, phone or email is invalid<br>BUSINESS_CLIENT_MISSING_CONTACT_INFO (200025) Client must have at least a phone or an email<br>BUSINESS_CLIENT_EXISTS (200004) Client with this phone or email already exists
         * Tag: business
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.business.microservice.route.api.ClientImportRequest]
         * Response: 200 application/x-protobuf [com.bookk.business.domain.api.client.entity.ClientImportResult] Created clients and rejected request positions
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] User is not allowed to create clients for this business
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Import clients errors<br>BUSINESS_CLIENT_IMPORT_TOO_LARGE (200039) A client import can contain at most 500 clients<br>BUSINESS_CLIENT_EXISTS (200004) A concurrent write took a phone or email of the import, retry it
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/business/import-clients.md
         */
        post<Api.Clients.Import> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<ClientImportRequest>()
            val importClients by application.injectScoped<ImportClients>(BusinessScope)

            call.respondWith(
                importClients(
                    requestUserId = principal.userId,
                    businessId = it.parent.businessId,
                    clients = body.clients.map(ClientImportEntry::toDetachedClient)
                )
            )
        }

        /**
         * Summary: Create client
         * Description: Create a new detached client within the business. userId in the body is ignored, a client is linked to an app user only by that user's own first booking with the business
         * Tag: business
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.business.domain.api.client.entity.ClientRemote]
         * Response: 200 application/x-protobuf [com.bookk.business.domain.api.client.entity.ClientRemote] Created client entity
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] User is not allowed to create clients for this business
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create client errors<br>BUSINESS_CLIENT_EXISTS (200004) Client with this phone or email already exists<br>BUSINESS_CLIENT_NAME_VALIDATION_ERROR (200005) Client name, last name, phone or email is invalid<br>BUSINESS_CLIENT_MISSING_CONTACT_INFO (200025) Client must have at least a phone or an email
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/business/create-client.md
         */
        post<Api.Clients> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<ClientRemote>()
            val createClient by application.injectScoped<CreateClient>(BusinessScope)

            call.respondWith(
                createClient(
                    requestUserId = principal.userId,
                    businessId = it.businessId,
                    client = body.toDetachedClient()
                )
            )
        }

        /**
         * Summary: Get clients
         * Description: Get clients list for specific business
         * Tag: business
         * Security: jwt
         */
        get<Api.Clients> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getClients by application.injectScoped<GetClients>(BusinessScope)

            call.respondWith(getClients(requestUserId = principal.userId, businessId = it.businessId))
        }.describe {
            responses {
                response(HttpStatusCode.OK.value) {
                    schema = jsonSchema<List<ClientRemote>>()
                    description = "List of clients"
                    ContentType.Application.ProtoBuf()
                }
                response(HttpStatusCode.Forbidden.value) {
                    schema = jsonSchema<SimpleServerError>()
                    description = "Caller is a suspended employee of this business - BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034)"
                    ContentType.Application.ProtoBuf()
                }
                response(HttpStatusCode.NotFound.value) {
                    schema = jsonSchema<SimpleServerError>()
                    description = "User is not allowed to read clients of this business"
                    ContentType.Application.ProtoBuf()
                }
            }
        }

        /**
         * Summary: Delete client
         * Description: Delete client by id
         * Tag: business
         * Security: jwt
         * Response: 204 application/x-protobuf Entity deleted
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Client not found or user is not allowed to delete it
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/business/delete-client.md
         */
        delete<Api.Clients.Id> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val deleteClient by application.injectScoped<DeleteClient>(BusinessScope)

            call.respondWith(
                deleteClient(
                    requestUserId = principal.userId,
                    businessId = it.parent.businessId,
                    id = it.id
                )
            )
        }

        /**
         * Summary: Update client
         * Description: Partially update a client's personal info and notes. Personal info (name, last name, phone, email) can only be changed for non-integrated (detached) clients<br>an integrated client's personal info is synced from its linked user profile, only its description can be edited
         * Tag: business
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.business.domain.api.client.entity.ClientUpdateModel] Non-null fields will be updated
         * Response: 200 application/x-protobuf [com.bookk.business.domain.api.client.entity.ClientRemote] Updated client entity
         * Response: 400 application/x-protobuf Path id does not match body id
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Client not found or user is not allowed to update it
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Update client errors<br>BUSINESS_CLIENT_EXISTS (200004) Client with this phone or email already exists<br>BUSINESS_CLIENT_NAME_VALIDATION_ERROR (200005) Client name, last name, phone or email is invalid<br>BUSINESS_CLIENT_PERSONAL_INFO_NOT_EDITABLE (200026) Personal info of an integrated client cannot be edited, only its description can
         * Response: 403 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Caller is a suspended employee of this business<br>BUSINESS_EMPLOYEE_ACCESS_SUSPENDED (200034) Your access to this business is suspended
         * See: docs/operations/business/update-client.md
         */
        patch<Api.Clients.Id> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<ClientUpdateModel>()
            val updateClient by application.injectScoped<UpdateClient>(BusinessScope)

            if (it.id != body.id) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request")
            } else {
                call.respondWith(
                    updateClient(
                        requestUserId = principal.userId,
                        businessId = it.parent.businessId,
                        model = body
                    )
                )
            }
        }
    }
}
