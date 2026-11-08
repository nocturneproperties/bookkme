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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import library.email.EmailContent
import library.email.EmailMessage
import library.email.EmailSendError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PostmarkEmailSenderTest {

    private class SutFixture(
        responses: List<Pair<HttpStatusCode, String>> = listOf(HttpStatusCode.OK to SENT),
    ) {
        val requests = mutableListOf<HttpRequestData>()
        val retryDelays = mutableListOf<Long>()
        private val remaining = ArrayDeque(responses)
        private val engine = MockEngine { request -> answer(request) }
        val sut = PostmarkEmailSender(
            httpClient = postmarkHttpClient(engine, retryDelay = { retryDelays += it }),
            serverToken = SERVER_TOKEN,
            from = FROM,
        )

        private fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
            requests += request
            val (status, body) = remaining.removeFirstOrNull() ?: (HttpStatusCode.OK to SENT)
            return respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }

        fun sentJson(index: Int = 0): JsonObject = Json.parseToJsonElement((requests[index].body as TextContent).text).jsonObject
    }

    @Test
    fun `should post the message to Postmark's transactional stream with the server token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val message = EmailMessage.stub(
            to = "client@example.com",
            content = EmailContent.stub(subject = "Your code", textBody = "Code 123456", htmlBody = "<p>Code 123456</p>", tag = "email-verification"),
        )

        whenn()
        val result = fixture.sut.send(message)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        val request = fixture.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://api.postmarkapp.com/email", request.url.toString())
        assertEquals(SERVER_TOKEN, request.headers["X-Postmark-Server-Token"])
        assertEquals(ContentType.Application.Json.toString(), request.headers[HttpHeaders.Accept])
        val body = fixture.sentJson()
        assertEquals(FROM, body.getValue("From").jsonPrimitive.content)
        assertEquals("client@example.com", body.getValue("To").jsonPrimitive.content)
        assertEquals("Your code", body.getValue("Subject").jsonPrimitive.content)
        assertEquals("Code 123456", body.getValue("TextBody").jsonPrimitive.content)
        assertEquals("<p>Code 123456</p>", body.getValue("HtmlBody").jsonPrimitive.content)
        assertEquals("outbound", body.getValue("MessageStream").jsonPrimitive.content)
        assertEquals("email-verification", body.getValue("Tag").jsonPrimitive.content)
    }

    @Test
    fun `should report an inactive recipient as undeliverable`() = runUnitTest {
        given()
        val fixture = SutFixture(listOf(HttpStatusCode.UnprocessableEntity to postmarkError(406)))

        whenn()
        val result = fixture.sut.send(EmailMessage.stub())

        then()
        assertTrue(result.exceptionOrNull() is EmailSendError.Undeliverable) { result.toString() }
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun `should report an invalid email request as undeliverable`() = runUnitTest {
        given()
        val fixture = SutFixture(listOf(HttpStatusCode.UnprocessableEntity to postmarkError(300)))

        whenn()
        val result = fixture.sut.send(EmailMessage.stub())

        then()
        assertTrue(result.exceptionOrNull() is EmailSendError.Undeliverable) { result.toString() }
    }

    @Test
    fun `should fail without blaming the address when Postmark rejects the request for another reason`() = runUnitTest {
        given()
        val fixture = SutFixture(listOf(HttpStatusCode.UnprocessableEntity to postmarkError(400)))

        whenn()
        val result = fixture.sut.send(EmailMessage.stub())

        then()
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is EmailSendError.Undeliverable)
    }

    @Test
    fun `should retry after Postmark's rate limit and succeed`() = runUnitTest {
        given()
        val fixture = SutFixture(listOf(HttpStatusCode.TooManyRequests to postmarkError(429), HttpStatusCode.OK to SENT))

        whenn()
        val result = fixture.sut.send(EmailMessage.stub())

        then()
        assertTrue(result.isSuccess) { result.toString() }
        assertEquals(2, fixture.requests.size)
        assertEquals(1, fixture.retryDelays.size)
    }

    @Test
    fun `should retry a server error twice and then fail`() = runUnitTest {
        given()
        val fixture = SutFixture(List(3) { HttpStatusCode.InternalServerError to "" })

        whenn()
        val result = fixture.sut.send(EmailMessage.stub())

        then()
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is EmailSendError.Undeliverable)
        assertEquals(3, fixture.requests.size)
    }

    @Test
    fun `should fail at once without retrying when the server token is refused`() = runUnitTest {
        given()
        val fixture = SutFixture(listOf(HttpStatusCode.Unauthorized to postmarkError(10)))

        whenn()
        val result = fixture.sut.send(EmailMessage.stub())

        then()
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is EmailSendError.Undeliverable)
        assertEquals(1, fixture.requests.size)
    }

    private companion object {
        const val SERVER_TOKEN = "POSTMARK_API_TEST"
        const val FROM = "no-reply@example.com"
        const val SENT = """{"To":"client@example.com","SubmittedAt":"2026-11-07T10:00:00Z","MessageID":"id","ErrorCode":0,"Message":"OK"}"""

        fun postmarkError(errorCode: Int) = """{"ErrorCode":$errorCode,"Message":"Postmark error $errorCode"}"""
    }
}
