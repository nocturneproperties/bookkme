package com.bookk.user.microservice.route.api.internal

import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.api.entity.TelegramUsernameBody
import com.bookk.user.domain.api.error.UserErrorCodes
import com.bookk.user.domain.api.operation.ChangeTelegramUsername
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

internal class InternalTelegramUsernameTest {

    @Test
    fun `should store the Telegram username of the user in the path`() = routeTest {
        given()
        val useCase: ChangeTelegramUsername = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, USERNAME) } returns Result.success(Unit)
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { postChangeTelegramUsername() })

        whenn()
        val response = createTestClient().post(UserRouting.Api.Internal.User.ChangeTelegramUsername(id = userId)) {
            setBody(TelegramUsernameBody(USERNAME))
        }

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
        coVerify(exactly = 1) { useCase.invoke(userId, USERNAME) }
    }

    @Test
    fun `should clear the Telegram username when the body has none`() = routeTest {
        given()
        val useCase: ChangeTelegramUsername = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, null) } returns Result.success(Unit)
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { postChangeTelegramUsername() })

        whenn()
        val response = createTestClient().post(UserRouting.Api.Internal.User.ChangeTelegramUsername(id = userId)) {
            setBody(TelegramUsernameBody(null))
        }

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
        coVerify(exactly = 1) { useCase.invoke(userId, null) }
    }

    @Test
    fun `should return 404 when the user does not exist`() = routeTest {
        given()
        val useCase: ChangeTelegramUsername = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, USERNAME) } returns
            Result.failure(ChangeTelegramUsername.Error.UserNotFound())
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { postChangeTelegramUsername() })

        whenn()
        val response = createTestClient().post(UserRouting.Api.Internal.User.ChangeTelegramUsername(id = userId)) {
            setBody(TelegramUsernameBody(USERNAME))
        }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(UserErrorCodes.USER_NOT_EXIST, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return 422 for a username that is not a Telegram username`() = routeTest {
        given()
        val useCase: ChangeTelegramUsername = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, "a b") } returns
            Result.failure(ChangeTelegramUsername.Error.UsernameInvalid())
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { postChangeTelegramUsername() })

        whenn()
        val response = createTestClient().post(UserRouting.Api.Internal.User.ChangeTelegramUsername(id = userId)) {
            setBody(TelegramUsernameBody("a b"))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(UserErrorCodes.TELEGRAM_USERNAME_INVALID, response.body<SimpleServerError>().errorCode)
    }

    private companion object {
        const val USERNAME = "ann_lee"
    }
}
