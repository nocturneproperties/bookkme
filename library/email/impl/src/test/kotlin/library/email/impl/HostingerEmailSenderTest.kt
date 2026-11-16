package library.email.impl

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import library.email.EmailContent
import library.email.EmailMessage
import library.email.EmailSendError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class HostingerEmailSenderTest {

    private class SutFixture(
        responses: List<Pair<HttpStatusCode, String>> = listOf(ACCEPTED),
    ) {
        val requests = mutableListOf<HttpRequestData>()
        val retryDelays = mutableListOf<Long>()
        private val remaining = ArrayDeque(responses)
        private val engine = MockEngine { request -> answer(request) }
        val sut = HostingerEmailSender(
            httpClient = hostingerMailHttpClient(engine, retryDelay = { retryDelays += it }),
            apiToken = API_TOKEN,
            mailboxId = MAILBOX_ID,
            senderName = SENDER_NAME,
        )

        private fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
            requests += request
            val (status, body) = remaining.removeFirstOrNull() ?: ACCEPTED
            return respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }

        fun sentJson(index: Int = 0): JsonObject {
            return Json.parseToJsonElement((requests[index].body as TextContent).text).jsonObject
        }
    }

    @Test
    fun `should post the message to the mailbox's send endpoint with the bearer token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val message = EmailMessage.stub(
            to = "client@example.com",
            content = EmailContent.stub(
                subject = "Your code",
                textBody = "Code 123456",
                htmlBody = "<p>Code 123456</p>",
            ),
        )

        whenn()
        val result = fixture.sut.send(message)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        val request = fixture.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://api.mail.hostinger.com/api/v1/mailboxes/$MAILBOX_ID/send", request.url.toString())
        assertEquals("Bearer $API_TOKEN", request.headers[HttpHeaders.Authorization])
        assertEquals(ContentType.Application.Json.toString(), request.headers[HttpHeaders.Accept])
        val body = fixture.sentJson()
        assertEquals(listOf("client@example.com"), body.getValue("to").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(SENDER_NAME, body.getValue("displayName").jsonPrimitive.content)
        assertEquals("Your code", body.getValue("subject").jsonPrimitive.content)
        assertEquals("Code 123456", body.getValue("text").jsonPrimitive.content)
        assertEquals("<p>Code 123456</p>", body.getValue("html").jsonPrimitive.content)
    }

    @Test
    fun `should report a recipient address the API refuses as undeliverable`() = runUnitTest {
        given()
        val fixture = SutFixture(listOf(HttpStatusCode.UnprocessableEntity to validationFailed("to.0")))

        whenn()
        val result = fixture.sut.send(EmailMessage.stub())

        then()
        assertTrue(result.exceptionOrNull() is EmailSendError.Undeliverable) { result.toString() }
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun `should report a refused recipient list as undeliverable`() = runUnitTest {
        given()
        val fixture = SutFixture(listOf(HttpStatusCode.UnprocessableEntity to validationFailed("to")))

        whenn()
        val result = fixture.sut.send(EmailMessage.stub())

        then()
        assertTrue(result.exceptionOrNull() is EmailSendError.Undeliverable) { result.toString() }
    }

    @Test
    fun `should fail without blaming the address when the API refuses another field`() = runUnitTest {
        given()
        val fixture = SutFixture(listOf(HttpStatusCode.UnprocessableEntity to validationFailed("subject")))

        whenn()
        val result = fixture.sut.send(EmailMessage.stub())

        then()
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is EmailSendError.Undeliverable)
    }

    @Test
    fun `should fail without blaming the address when a refusal names no fields`() = runUnitTest {
        given()
        val refusal = """{"error":"The given data was invalid.","code":"ERR_VALIDATION_FAILED","params":[]}"""
        val fixture = SutFixture(listOf(HttpStatusCode.UnprocessableEntity to refusal))

        whenn()
        val result = fixture.sut.send(EmailMessage.stub())

        then()
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is EmailSendError.Undeliverable)
    }

    @Test
    fun `should retry after the rate limit and succeed`() = runUnitTest {
        given()
        val fixture = SutFixture(listOf(HttpStatusCode.TooManyRequests to error("ERR_RATE_LIMIT"), ACCEPTED))

        whenn()
        val result = fixture.sut.send(EmailMessage.stub())

        then()
        assertTrue(result.isSuccess) { result.toString() }
        assertEquals(2, fixture.requests.size)
        assertEquals(1, fixture.retryDelays.size)
    }

    @Test
    fun `should retry an upstream failure twice and then fail`() = runUnitTest {
        given()
        val fixture = SutFixture(List(3) { HttpStatusCode.BadGateway to error("ERR_UPSTREAM_UNAVAILABLE") })

        whenn()
        val result = fixture.sut.send(EmailMessage.stub())

        then()
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is EmailSendError.Undeliverable)
        assertEquals(3, fixture.requests.size)
    }

    @Test
    fun `should fail at once without retrying when the token is refused`() = runUnitTest {
        given()
        val fixture = SutFixture(listOf(HttpStatusCode.Unauthorized to error("ERR_UNAUTHORIZED")))

        whenn()
        val result = fixture.sut.send(EmailMessage.stub())

        then()
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is EmailSendError.Undeliverable)
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun `should fail at once without retrying when the token may not send from the mailbox`() = runUnitTest {
        given()
        val fixture = SutFixture(listOf(HttpStatusCode.Forbidden to error("ERR_FORBIDDEN")))

        whenn()
        val result = fixture.sut.send(EmailMessage.stub())

        then()
        assertTrue(result.isFailure)
        assertEquals(1, fixture.requests.size)
    }

    private companion object {
        const val API_TOKEN = "hostinger-mail-token"
        const val MAILBOX_ID = "AC1a2b3c4d5e6f7g"
        const val SENDER_NAME = "BookkMe"
        val ACCEPTED = HttpStatusCode.NoContent to ""

        fun error(code: String): String {
            return """{"error":"Hostinger error $code","code":"$code","params":{}}"""
        }

        fun validationFailed(field: String): String {
            return """{"error":"The given data was invalid.","code":"ERR_VALIDATION_FAILED",""" +
                """"params":{"$field":["$field is invalid"]}}"""
        }
    }
}
