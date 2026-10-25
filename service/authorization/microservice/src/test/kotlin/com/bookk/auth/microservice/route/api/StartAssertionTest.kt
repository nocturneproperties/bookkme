package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.authentication.entity.AssertionStartResponse
import com.bookk.auth.domain.api.authentication.operation.StartAssertion
import com.bookk.auth.microservice.route.AuthRouting
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.uuid.Uuid

internal class StartAssertionTest {

    @Test
    fun `should return OK when sign in challenge is successfully started`() = routeTest {
        given()
        val useCase: StartAssertion = mockk()
        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { signIn() }
        )
        val client = createTestClient()
        val answer = AssertionStartResponse(Uuid.random().toString(), "mock", "mock")
        coEvery { useCase.invoke() } returns Result.success(answer)

        whenn()
        val response = client.get(AuthRouting.Api.Auth.PassKey.SignInChallenge())
        val body = response.body<AssertionStartResponse>()

        then()
        coVerify { useCase.invoke() }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(answer, body)
    }
}