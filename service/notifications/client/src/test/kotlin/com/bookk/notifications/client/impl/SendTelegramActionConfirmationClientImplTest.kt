package com.bookk.notifications.client.impl

import com.bookk.core.client.internalHttpClient
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationDelivery
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationMessage
import com.bookk.notifications.client.api.telegram.TelegramConfirmedAction
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class SendTelegramActionConfirmationClientImplTest {

    private class SutFixture(
        status: HttpStatusCode = HttpStatusCode.OK,
        val delivery: TelegramActionConfirmationDelivery = TelegramActionConfirmationDelivery.stub(),
    ) {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val sentMessages = mutableListOf<TelegramActionConfirmationMessage>()
        val sut = SendTelegramActionConfirmationClientImpl(
            internalHttpClient(
                engineFactory = MockEngine,
                hostnameVariable = "SERVICE_HOSTNAME",
                clientTag = "auth",
                environment = { "notifications.service" },
            ) {
                addHandler { request ->
                    requests += request.method to request.url.encodedPath
                    sentMessages += ProtoBuf.decodeFromByteArray<TelegramActionConfirmationMessage>(
                        (request.body as OutgoingContent.ByteArrayContent).bytes(),
                    )
                    respond(
                        content = ProtoBuf.encodeToByteArray(delivery),
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.ProtoBuf.toString()),
                    )
                }
            },
        )
    }

    @Test
    fun `should post the message to notifications' internal Telegram confirmation route`() = runUnitTest {
        given()
        val fixture = SutFixture(
            delivery = TelegramActionConfirmationDelivery.stub(TelegramActionConfirmationDelivery.Outcome.NO_CHAT),
        )
        val userId = Uuid.random()
        val message = TelegramActionConfirmationMessage.stub(
            action = TelegramConfirmedAction.EXPORT_PERSONAL_DATA,
            language = Language.PL,
        )

        whenn()
        val result = fixture.sut(userId, message)

        then()
        assertEquals(fixture.delivery, result.getOrNull())
        assertEquals(
            listOf(HttpMethod.Post to "/api/internal/telegram/action_confirmation/$userId"),
            fixture.requests,
        )
        assertEquals(listOf(message), fixture.sentMessages)
    }

    @Test
    fun `should send the message once without retrying when notifications fails`() = runUnitTest {
        given()
        val fixture = SutFixture(status = HttpStatusCode.InternalServerError)

        whenn()
        val result = fixture.sut(Uuid.random(), TelegramActionConfirmationMessage.stub())

        then()
        assertTrue(result.isFailure)
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun `should fail when notifications can't send the message`() = runUnitTest {
        given()
        val fixture = SutFixture(status = HttpStatusCode.BadRequest)

        whenn()
        val result = fixture.sut(Uuid.random(), TelegramActionConfirmationMessage.stub())

        then()
        assertTrue(result.isFailure)
    }
}
