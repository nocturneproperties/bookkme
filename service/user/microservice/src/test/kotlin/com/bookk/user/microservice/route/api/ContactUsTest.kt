package com.bookk.user.microservice.route.api

import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AppPrincipal
import com.bookk.user.domain.api.operation.CreateContactForm
import com.bookk.user.microservice.route.UserRouting
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class ContactUsTest {

    private val userId = Uuid.random()

    @Test
    fun `should post contact form`() = routeTest {
        given()
        val useCase: CreateContactForm = mockk()
        val text = "Test message"
        val logs = "Test logs"
        
        coEvery { useCase.invoke(any()) } returns Result.success(Unit)
        
        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { postContactForm() }
        )
        
        whenn()
        val client = createTestClient()
        val response = client.post(UserRouting.Api.User.ContactUs()) {
            setBody(ContactFormBody(text, logs))
        }
        
        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
    }
}
