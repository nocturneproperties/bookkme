package com.bookk.server

import com.bookk.appointments.microservice.route.appointmentsRoute
import com.bookk.auth.microservice.route.authRoute
import com.bookk.business.microservice.route.businessRoute
import com.bookk.core.service.ServiceConfig
import com.bookk.core.service.installNegotiation
import com.bookk.core.service.installSwagger
import com.bookk.core.service.test.routeTest
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.microservice.route.notificationsRoute
import com.bookk.user.microservice.route.userRoute
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.resources.Resources
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test

internal class SwaggerTest {

    private class SutFixture(val services: List<Service>) {
        val serviceName = services.singleOrNull()?.name?.lowercase() ?: "monolith"
        val swaggerPath = "/api/$serviceName/internal/swagger"

        fun install(routing: Routing) {
            routing.installSwagger(ServiceConfig(title = "Test", version = "test", root = "api/$serviceName"))
            if (services.size == 1) {
                services.single().routes(routing)
            } else {
                routing.monolithRoute()
            }
        }
    }

    private enum class Service(val routes: Routing.() -> Unit, val requests: Set<String>) {
        AUTH(
            Routing::authRoute,
            setOf(
                "post /api/auth/passkey/sign_up/challenge",
                "post /api/auth/sign_up",
                "post /api/auth/sign_in",
                "post /api/auth/passkey/add/finish",
                "post /api/auth/passkey/confirm_action",
                "delete /api/auth/account",
                "post /api/auth/email",
                "post /api/auth/email/confirm",
            ),
        ),
        USER(
            Routing::userRoute,
            setOf(
                "post /api/internal/user/{id}/email",
                "get /api/internal/user/email",
                "post /api/internal/user",
                "patch /api/user/me",
                "post /api/user/contactus",
                "post /api/user/me/legal",
                "post /api/user/export",
            ),
        ),
        BUSINESS(
            Routing::businessRoute,
            setOf(
                "post /api/business",
                "put /api/business/{id}",
                "post /api/business/{id}/export",
                "post /api/business/{businessId}/clients/import",
                "post /api/business/{businessId}/clients",
                "delete /api/business/{businessId}/clients/{id}",
                "patch /api/business/{businessId}/clients/{id}",
                "post /api/business/{businessId}/service",
                "put /api/business/{businessId}/service/{id}",
                "post /api/business/{businessId}/service_group",
                "post /api/business/{businessId}/service/quote",
                "post /api/business/employee_invitation/redeem",
                "put /api/business/{businessId}/employee/{id}",
                "put /api/business/{businessId}/employee/{id}/permissions",
                "put /api/business/{businessId}/employee/{id}/suspension",
                "post /api/internal/business/{id}/appointment-booking-context",
                "post /api/internal/business/{id}/staff-booking-context",
                "post /api/internal/business/{id}/appointment-reschedule-context",
                "post /api/internal/business/{id}/services",
            ),
        ),
        APPOINTMENTS(
            Routing::appointmentsRoute,
            setOf(
                "put /api/appointments/{id}",
                "post /api/appointments",
                "post /api/appointments/instant",
                "post /api/appointments/{id}/cancel",
                "post /api/appointments/{id}/complete",
                "put /api/appointments/settings/{businessId}",
                "post /api/appointments/request",
                "post /api/appointments/request/{id}/decline",
            ),
        ),
        NOTIFICATIONS(
            Routing::notificationsRoute,
            setOf(
                "put /api/notifications/{deviceUuid}/token",
                "put /api/notifications/settings",
            ),
        ),
    }

    @Test
    fun `should publish auth request bodies and operation documentation`() = assertSwagger(Service.AUTH)

    @Test
    fun `should publish user request bodies and operation documentation`() = assertSwagger(Service.USER)

    @Test
    fun `should publish business request bodies and operation documentation`() = assertSwagger(Service.BUSINESS)

    @Test
    fun `should publish appointment request bodies and operation documentation`() = assertSwagger(Service.APPOINTMENTS)

    @Test
    fun `should publish notification request bodies and operation documentation`() = assertSwagger(Service.NOTIFICATIONS)

    @Test
    fun `should preserve every service contract in the monolith swagger document`() = assertSwagger(*Service.entries.toTypedArray())

    private val listResponses = mapOf(
        Service.APPOINTMENTS to mapOf(
            "get /api/appointments/list/{businessId}" to "Appointment",
            "get /api/appointments/enabled" to "BusinessAppointmentsEnabled",
            "get /api/appointments/request/{businessId}" to "AppointmentRequest",
            "get /api/appointments/request/{businessId}/mine" to "AppointmentRequest",
        ),
        Service.BUSINESS to mapOf(
            "get /api/business/{businessId}/employee" to "Employee",
            "get /api/business/{businessId}/employee_invitation" to "EmployeeInvitation",
            "get /api/business/{businessId}/service" to "Service",
            "get /api/business/{businessId}/service_group" to "ServiceGroup",
            "get /api/business/{businessId}/clients" to "ClientRemote",
            "post /api/internal/business/{id}/services" to "Service",
            "get /api/internal/client/{userId}/businesses" to "Uuid",
            "get /api/internal/owner/{userId}/members" to "Uuid",
        ),
        Service.USER to mapOf(
            "get /api/user/legal" to "LegalDocumentVersion",
            "get /api/user/me/legal" to "LegalDocumentStatus",
        ),
    )

    private fun assertSwagger(vararg services: Service) = routeTest {
        given()
        val fixture = SutFixture(services.toList())
        application {
            install(Resources)
            install(Authentication) {
                bearer { authenticate { null } }
            }
            routing {
                installNegotiation()
                fixture.install(this)
            }
        }

        whenn()
        val response = client.get("${fixture.swaggerPath}/documentation.yaml")
        val document = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        val operations = document.getValue("paths").jsonObject.flatMap { (path, methods) ->
            methods.jsonObject.filterKeys { it in setOf("get", "post", "put", "patch", "delete") }
                .map { (method, operation) -> "$method $path" to operation.jsonObject }
        }.toMap()
        val expectedRequests = services.flatMap { it.requests }.toSet()
        val actualRequests = operations.filterValues { "requestBody" in it }.keys
        assertEquals(expectedRequests, actualRequests, "Operations with a documented request body")
        expectedRequests.forEach { operation ->
            val body = operations.getValue(operation).getValue("requestBody").jsonObject
            val content = body.getValue("content").jsonObject
            assertTrue("application/x-protobuf" in content, "$operation must document the ProtoBuf request content type")
            val mediaType = content.getValue("application/x-protobuf").jsonObject
            assertTrue("schema" in mediaType, "$operation must document its request schema")
            val schema = mediaType.getValue("schema").jsonObject
            val resolved = resolveSchema(document, schema)
            assertFalse(resolved["properties"]?.jsonObject.isNullOrEmpty(), "$operation must describe the request fields")
        }
        operations.filterKeys { !it.endsWith("/healthcheck") && !it.endsWith("/jwks.json") }.forEach { (operation, metadata) ->
            assertFalse(metadata["summary"]?.jsonPrimitive?.content.isNullOrBlank(), "$operation must have a summary")
            assertFalse(metadata["responses"]?.jsonObject.isNullOrEmpty(), "$operation must document its responses")
        }
        services.flatMap { listResponses[it].orEmpty().entries }.forEach { (operation, itemType) ->
            val responseSchema = operations.getValue(operation).getValue("responses").jsonObject.getValue("200").jsonObject
                .getValue("content").jsonObject.getValue("application/protobuf").jsonObject.getValue("schema").jsonObject
            assertEquals("array", responseSchema.getValue("type").jsonPrimitive.content, operation)
            val items = responseSchema.getValue("items").jsonObject
            if (itemType == "Uuid") {
                assertEquals("string", items.getValue("type").jsonPrimitive.content, operation)
                assertEquals("uuid", items.getValue("format").jsonPrimitive.content, operation)
            } else {
                assertEquals("#/components/schemas/$itemType", items.getValue("\$ref").jsonPrimitive.content, operation)
                assertFalse(resolveSchema(document, items)["properties"]?.jsonObject.isNullOrEmpty(), "$operation must describe the list item fields")
            }
        }
        assertSchemaReferences(document, document)
        if (Service.APPOINTMENTS in services) {
            val appointment = operations.getValue("get /api/appointments/{id}")
            assertTrue(appointment.getValue("responses").jsonObject.keys.containsAll(setOf("200", "401", "403", "404")))
            val schema = appointment.getValue("responses").jsonObject.getValue("200").jsonObject
                .getValue("content").jsonObject.getValue("application/x-protobuf").jsonObject.getValue("schema").jsonObject
            assertEquals("#/components/schemas/Appointment", schema.getValue("\$ref").jsonPrimitive.content)
            assertFalse(appointment["security"]?.let { it as JsonArray }.isNullOrEmpty())
            val notFoundContent = appointment.getValue("responses").jsonObject.getValue("404").jsonObject.getValue("content").jsonObject
            assertTrue("text/plain" in notFoundContent)
        }
        if (Service.NOTIFICATIONS in services) {
            val update = operations.getValue("put /api/notifications/settings")
            val schema = update.getValue("requestBody").jsonObject.getValue("content").jsonObject
                .getValue("application/x-protobuf").jsonObject.getValue("schema").jsonObject
            assertEquals("#/components/schemas/NotificationSettingsUpdate", schema.getValue("\$ref").jsonPrimitive.content)
            assertEquals(setOf("id", "appointmentEnabled", "channels"), resolveSchema(document, schema).getValue("properties").jsonObject.keys)
        }
        if (Service.AUTH in services) {
            val signUp = operations.getValue("post /api/auth/sign_up")
            val schema = signUp.getValue("requestBody").jsonObject.getValue("content").jsonObject
                .getValue("application/x-protobuf").jsonObject.getValue("schema").jsonObject
            val resolved = resolveSchema(document, schema)
            assertTrue("acceptedDocuments" in resolved.getValue("properties").jsonObject)
            assertTrue(JsonPrimitive("acceptedDocuments") in (resolved.getValue("required") as JsonArray))
            assertTrue(listOf("verificationId", "emailCode").all { JsonPrimitive(it) in (resolved.getValue("required") as JsonArray) }) { resolved.toString() }
            val challenge = operations.getValue("post /api/auth/passkey/sign_up/challenge")
            val challengeSchema = challenge.getValue("responses").jsonObject.getValue("200").jsonObject.getValue("content").jsonObject
                .getValue("application/x-protobuf").jsonObject.getValue("schema").jsonObject
            assertTrue("emailVerification" in resolveSchema(document, challengeSchema).getValue("properties").jsonObject) { challengeSchema.toString() }
            assertTrue(challenge.getValue("responses").jsonObject.keys.containsAll(setOf("200", "422", "429")))
            val confirmEmail = operations.getValue("post /api/auth/email/confirm")
            val confirmSchema = confirmEmail.getValue("requestBody").jsonObject.getValue("content").jsonObject
                .getValue("application/x-protobuf").jsonObject.getValue("schema").jsonObject
            assertEquals(setOf("verificationId", "code"), resolveSchema(document, confirmSchema).getValue("properties").jsonObject.keys)
            val deletion = operations.getValue("delete /api/auth/account")
            val deletionSchema = deletion.getValue("requestBody").jsonObject.getValue("content").jsonObject
                .getValue("application/x-protobuf").jsonObject.getValue("schema").jsonObject
            assertEquals("#/components/schemas/DeleteAccountRequest", deletionSchema.getValue("\$ref").jsonPrimitive.content)
            assertEquals(setOf("confirmationToken"), resolveSchema(document, deletionSchema).getValue("properties").jsonObject.keys)
            assertTrue(deletion.getValue("responses").jsonObject.keys.containsAll(setOf("204", "403", "422")))
        }
    }

    private fun resolveSchema(document: JsonObject, schema: JsonObject): JsonObject {
        val reference = schema["\$ref"]?.jsonPrimitive?.content ?: return schema
        return document.getValue("components").jsonObject.getValue("schemas").jsonObject
            .getValue(reference.removePrefix("#/components/schemas/")).jsonObject
    }

    private fun assertSchemaReferences(document: JsonObject, element: JsonElement) {
        when (element) {
            is JsonObject -> {
                element["\$ref"]?.jsonPrimitive?.content?.let { reference ->
                    assertTrue(reference.startsWith("#/components/schemas/"), "Unexpected schema reference $reference")
                    assertFalse(resolveSchema(document, element).isEmpty(), "Unresolved schema reference $reference")
                }
                (element["description"] as? JsonPrimitive)?.content?.let { description ->
                    assertFalse(description.startsWith("Failed to resolve schema"), description)
                }
                element.values.forEach { assertSchemaReferences(document, it) }
            }

            is JsonArray -> element.forEach { assertSchemaReferences(document, it) }

            else -> Unit
        }
    }
}
