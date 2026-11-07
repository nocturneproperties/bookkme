package com.bookk.core.client

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

internal class InternalHttpClientTest {

    private class SutFixture(
        hostname: String? = "business.service",
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respond("", HttpStatusCode.OK) },
    ) {
        val requests = mutableListOf<HttpRequestData>()
        val sut = internalHttpClient(
            engineFactory = MockEngine,
            hostnameVariable = HOSTNAME_VARIABLE,
            clientTag = "user",
            environment = { variable -> hostname.takeIf { variable == HOSTNAME_VARIABLE } },
        ) {
            addHandler { request ->
                requests += request
                handler(request)
            }
        }
    }

    @Test
    fun `should call the service's host over plain HTTP and name the calling service`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut.get("/api/internal/export/business/user")

        then()
        val request = fixture.requests.single()
        assertEquals("http://business.service/api/internal/export/business/user", request.url.toString())
        assertEquals("user", request.headers[HttpHeaders.UserAgent])
    }

    @Test
    fun `should give every call its own idempotency key`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut.get("/first")
        fixture.sut.get("/second")

        then()
        val keys = fixture.requests.map { it.headers["Idempotency-Key"] }
        assertNotNull(keys[0])
        assertNotEquals(keys[0], keys[1])
    }

    @Test
    fun `should retry a call that failed on the server`() = runUnitTest {
        given()
        val calls = AtomicInteger()
        val fixture = SutFixture { if (calls.incrementAndGet() == 1) respond("", HttpStatusCode.ServiceUnavailable) else respond("", HttpStatusCode.OK) }

        whenn()
        val response = fixture.sut.get("/flaky")

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, fixture.requests.size)
    }

    @Test
    fun `should refuse to call anywhere when the service's hostname is not set, naming the variable`() = runUnitTest {
        given()
        val fixture = SutFixture(hostname = null)

        whenn()
        val result = runCatching { fixture.sut.get("/api/internal/export/business/user") }

        then()
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains(HOSTNAME_VARIABLE)) { result.exceptionOrNull().toString() }
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `should close its engine together with the client`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut.close()

        then()
        assertFalse(fixture.sut.engine.coroutineContext[Job]!!.isActive)
    }

    private companion object {
        const val HOSTNAME_VARIABLE = "APPLICATION_BUSINESS_SERVICE_HOSTNAME"
    }
}
