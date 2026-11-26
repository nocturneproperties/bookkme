package com.bookk.user.microservice.route.api.internal

import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.api.entity.EmailBody
import com.bookk.user.domain.api.entity.ProfileName
import com.bookk.user.domain.api.entity.VerifiedEmailBody
import com.bookk.user.domain.api.error.UserErrorCodes
import com.bookk.user.domain.api.operation.ChangeUserEmail
import com.bookk.user.microservice.route.UserRouting
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class InternalUserEmailTest {

    @Test
    fun `should store the verified email of the user in the path`() = routeTest {
        given()
        val useCase: ChangeUserEmail = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, EMAIL, null) } returns Result.success(Unit)
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { postChangeUserEmail() })

        whenn()
        val response = createTestClient().post(UserRouting.Api.Internal.User.ChangeEmail(id = userId)) {
            setBody(EmailBody(EMAIL))
        }

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
        coVerify(exactly = 1) { useCase.invoke(userId, EMAIL, null) }
    }

    @Test
    fun `should return 404 when the user does not exist`() = routeTest {
        given()
        val useCase: ChangeUserEmail = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, EMAIL, null) } returns Result.failure(ChangeUserEmail.Error.UserNotFound())
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { postChangeUserEmail() })

        whenn()
        val response = createTestClient().post(UserRouting.Api.Internal.User.ChangeEmail(id = userId)) {
            setBody(EmailBody(EMAIL))
        }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(UserErrorCodes.USER_NOT_EXIST, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return 422 when another account holds the email`() = routeTest {
        given()
        val useCase: ChangeUserEmail = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, EMAIL, null) } returns Result.failure(ChangeUserEmail.Error.EmailTaken())
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { postChangeUserEmail() })

        whenn()
        val response = createTestClient().post(UserRouting.Api.Internal.User.ChangeEmail(id = userId)) {
            setBody(EmailBody(EMAIL))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(UserErrorCodes.USER_CONTACT_TAKEN, response.body<SimpleServerError>().errorCode)
    }

    private companion object {
        const val EMAIL = "new@example.com"
    }

    @Test
    fun `should store the name sent with the verified email`() = routeTest {
        given()
        val useCase: ChangeUserEmail = mockk()
        val userId = Uuid.random()
        val name = ProfileName.stub(name = "Ann", lastName = "Lee")
        coEvery { useCase.invoke(userId, EMAIL, name) } returns Result.success(Unit)
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { postChangeUserEmail() })

        whenn()
        val response = createTestClient().post(UserRouting.Api.Internal.User.ChangeEmail(id = userId)) {
            setBody(VerifiedEmailBody(EMAIL, name))
        }

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
        coVerify(exactly = 1) { useCase.invoke(userId, EMAIL, name) }
    }
}
