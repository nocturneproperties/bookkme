package com.bookk.user.microservice.route.api

import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.microservice.route.UserRouting
import io.ktor.client.plugins.resources.get
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module

internal class HealthCheckTest {
    @Test
    fun `should return OK when health check is called`() = routeTest {
        given()
        setupApplication(
            diModule = module {},
            routeUnderTest = { getHealthCheck() }
        )
        whenn()
        val client = createTestClient()
        val response = client.get(UserRouting.Api.User.HealthCheck())
        
        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
