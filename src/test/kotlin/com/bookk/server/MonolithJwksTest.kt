package com.bookk.server

import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.startScopedApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.resources.Resources
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import library.signing.GetVerificationKeys
import library.signing.SigningKey
import library.signing.SigningKeyStatus
import library.signing.route.jwks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MonolithJwksTest {

    private class SutFixture {
        val authKeys = listOf(SigningKey.stub(publicKeyPem = PUBLIC_KEY))
        val businessKeys = listOf(
            SigningKey.stub(publicKeyPem = PUBLIC_KEY),
            SigningKey.stub(publicKeyPem = PUBLIC_KEY, status = SigningKeyStatus.RETIRING),
        )
        private val authVerificationKeys = mockk<GetVerificationKeys>()
        private val businessVerificationKeys = mockk<GetVerificationKeys>()

        init {
            coEvery { authVerificationKeys() } returns Result.success(authKeys)
            coEvery { businessVerificationKeys() } returns Result.success(businessKeys)
        }

        suspend fun start(builder: ApplicationTestBuilder, routes: Routing.() -> Unit) {
            builder.application {
                install(Resources)
                install(Authentication) {
                    bearer { authenticate { null } }
                }
                routing(routes)
            }
            builder.startScopedApplication(
                AuthScope to { scoped { authVerificationKeys } },
                BusinessScope to { scoped { businessVerificationKeys } },
            )
        }
    }

    @Test
    fun `should publish auth and business verification keys from the monolith`() = routeTest {
        given()
        val fixture = SutFixture()
        fixture.start(this) { monolithRoute() }

        whenn()
        val response = client.get("/jwks.json")

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        val keys = Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("keys").jsonArray
        assertEquals(
            (fixture.authKeys + fixture.businessKeys).map { it.id.toString() }.toSet(),
            keys.map { it.jsonObject.getValue("kid").jsonPrimitive.content }.toSet(),
        )
    }

    @Test
    fun `should publish only auth keys from the standalone auth service's endpoint`() = routeTest {
        given()
        val fixture = SutFixture()
        fixture.start(this) { jwks(AuthScope) }

        whenn()
        val response = client.get("/jwks.json")

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        val keys = Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("keys").jsonArray
        assertEquals(
            fixture.authKeys.map { it.id.toString() },
            keys.map { it.jsonObject.getValue("kid").jsonPrimitive.content },
        )
    }

    @Test
    fun `should publish only business keys from the standalone business service's endpoint`() = routeTest {
        given()
        val fixture = SutFixture()
        fixture.start(this) { jwks(BusinessScope) }

        whenn()
        val response = client.get("/jwks.json")

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        val keys = Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("keys").jsonArray
        assertEquals(
            fixture.businessKeys.map { it.id.toString() },
            keys.map { it.jsonObject.getValue("kid").jsonPrimitive.content },
        )
    }

    private companion object {
        val PUBLIC_KEY = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAocJ2lrDaHLgyBK3eO9V2
            0lM8HrTOVeG4b6NDKqoyX7jqb3GIoYv7BFPneTpHS00eoa7FfoIKC9YZi2CrzgoO
            k718ZcjQqJb8lFltHkFGV9zmyQcCsXY9OzZ3DYUFqT6yxg3jIMdtSyjpxZ4KvChf
            90b9pvDuKT3FS4Jzi0U2YeBQwOp1JSfYkkmbyvTQhlRrLtMAzGW8mivClsOmYuoR
            YxdP7t8ZHL1Fc8r71TZTjFAEDBHn4adIir8PD9QsHAwgqUUewT3JA14waEhv+29n
            2nr+jFsRNiohx8ZEcy2erC27zXxB87U3ylMQCl2IuZVmI+S09gDNN0M5iuiPDIMt
            CQIDAQAB
            -----END PUBLIC KEY-----
        """.trimIndent()
    }
}
