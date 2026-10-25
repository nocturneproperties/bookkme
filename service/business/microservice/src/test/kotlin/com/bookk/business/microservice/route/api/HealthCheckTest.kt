package com.bookk.business.microservice.route.api

import com.bookk.business.microservice.route.BusinessRouting
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.installTestPlugins
import com.bookk.core.service.test.routeTest
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.client.plugins.resources.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HealthCheckTest {
    @Test
    fun `should return OK when health check is called`() = routeTest {
        given()
        application {
            installTestPlugins()
            routing {
                healthCheck()
            }
        }
        whenn()
        val client = createTestClient()
        val response = client.get(BusinessRouting.Api.Business.HealthCheck())
        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
