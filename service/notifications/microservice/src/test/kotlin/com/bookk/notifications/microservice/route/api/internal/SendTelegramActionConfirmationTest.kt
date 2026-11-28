package com.bookk.notifications.microservice.route.api.internal

import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.client.api.telegram.SendTelegramActionConfirmation
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationDelivery
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationMessage
import com.bookk.notifications.microservice.route.NotificationsRouting.Api
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class SendTelegramActionConfirmationTest {

    private val userId = Uuid.random()
    private val message = TelegramActionConfirmationMessage.stub()

    @Test
    fun `should answer how the message was delivered`() = routeTest {
        given()
        val useCase: SendTelegramActionConfirmation = mockk()
        val delivery = TelegramActionConfirmationDelivery.stub(
            TelegramActionConfirmationDelivery.Outcome.CHAT_UNREACHABLE,
        )
        coEvery { useCase.invoke(userId, message) } returns Result.success(delivery)
        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { sendTelegramActionConfirmation() },
        )

        whenn()
        val response = createTestClient().post(Api.Internal.Telegram.ActionConfirmation(userId = userId)) {
            setBody(message)
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(delivery, response.body<TelegramActionConfirmationDelivery>())
    }

    @Test
    fun `should answer 500 when the Bot API can't be reached`() = routeTest {
        given()
        val useCase: SendTelegramActionConfirmation = mockk()
        coEvery { useCase.invoke(userId, message) } returns Result.failure(IllegalStateException("timeout"))
        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { sendTelegramActionConfirmation() },
        )

        whenn()
        val response = createTestClient().post(Api.Internal.Telegram.ActionConfirmation(userId = userId)) {
            setBody(message)
        }

        then()
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }
}
