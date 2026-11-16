package library.email.impl

import com.bookk.core.test.TestClock
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
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal class HostingerDeleteExpiredSentEmailsTest {

    private class SutFixture(
        searchResponses: List<Pair<HttpStatusCode, String>>,
        private val deleteStatus: HttpStatusCode = HttpStatusCode.NoContent,
    ) {
        val clock = TestClock(NOW)
        val searches = mutableListOf<HttpRequestData>()
        val deletions = mutableListOf<HttpRequestData>()
        private val remainingSearches = ArrayDeque(searchResponses)
        private val lastSearch = searchResponses.last()
        private val engine = MockEngine { request -> answer(request) }
        val sut = HostingerDeleteExpiredSentEmails(
            httpClient = hostingerMailHttpClient(engine, retryDelay = {}),
            apiToken = API_TOKEN,
            mailboxId = MAILBOX_ID,
            clock = clock,
        )

        private fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
            val (status, body) = if (request.url.encodedPath.endsWith("/search")) {
                searches += request
                remainingSearches.removeFirstOrNull() ?: lastSearch
            } else {
                deletions += request
                deleteStatus to ""
            }
            return respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }

        fun json(request: HttpRequestData): JsonObject {
            return Json.parseToJsonElement((request.body as TextContent).text).jsonObject
        }

        fun deletedUids(index: Int = 0): List<Long> {
            return json(deletions[index]).getValue("uids").jsonArray.map { it.jsonPrimitive.long }
        }
    }

    @Test
    fun `should delete the sent copies older than 5 years`() = runUnitTest {
        given()
        val fixture = SutFixture(
            listOf(
                HttpStatusCode.OK to page(
                    message(uid = 1, sentAt = CUTOFF - 1.days),
                    message(uid = 2, sentAt = CUTOFF),
                    message(uid = 3, sentAt = CUTOFF + 1.minutes),
                ),
            ),
        )

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        assertEquals(listOf(1L, 2L), fixture.deletedUids())
        val deletion = fixture.deletions.single()
        assertEquals(HttpMethod.Post, deletion.method)
        assertEquals(
            "https://api.mail.hostinger.com/api/v1/mailboxes/$MAILBOX_ID/folders/INBOX.Sent/messages/delete",
            deletion.url.toString(),
        )
        assertEquals("Bearer $API_TOKEN", deletion.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `should search the sent folder oldest first, only for copies near the retention limit`() = runUnitTest {
        given()
        val fixture = SutFixture(listOf(HttpStatusCode.OK to page()))

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        val search = fixture.searches.single()
        assertEquals(HttpMethod.Post, search.method)
        assertEquals(
            "/api/v1/mailboxes/$MAILBOX_ID/folders/INBOX.Sent/messages/search",
            search.url.encodedPath,
        )
        assertEquals("date", search.url.parameters["sort"])
        assertEquals("100", search.url.parameters["perPage"])
        assertEquals("Bearer $API_TOKEN", search.headers[HttpHeaders.Authorization])
        assertEquals("2022-01-17", fixture.json(search).getValue("before").jsonPrimitive.content)
    }

    @Test
    fun `should delete nothing when every sent copy is younger than 5 years`() = runUnitTest {
        given()
        val fixture = SutFixture(listOf(HttpStatusCode.OK to page(message(uid = 3, sentAt = CUTOFF + 1.days))))

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        assertTrue(fixture.deletions.isEmpty())
    }

    @Test
    fun `should keep a sent copy without a date`() = runUnitTest {
        given()
        val fixture = SutFixture(listOf(HttpStatusCode.OK to page("""{"uid":7}""")))

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        assertTrue(fixture.deletions.isEmpty())
    }

    @Test
    fun `should keep deleting full pages until it reaches copies younger than 5 years`() = runUnitTest {
        given()
        val fullPage = page(*Array(100) { message(uid = it + 1L, sentAt = CUTOFF - 30.days) })
        val lastPage = page(message(uid = 101, sentAt = CUTOFF - 1.days), message(uid = 102, sentAt = NOW - 1.days))
        val fixture = SutFixture(listOf(HttpStatusCode.OK to fullPage, HttpStatusCode.OK to lastPage))

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        assertEquals(2, fixture.searches.size)
        assertEquals((1L..100L).toList(), fixture.deletedUids(0))
        assertEquals(listOf(101L), fixture.deletedUids(1))
    }

    @Test
    fun `should stop after 500 full pages in one run`() = runUnitTest {
        given()
        val fullPage = page(*Array(100) { message(uid = it + 1L, sentAt = CUTOFF - 30.days) })
        val fixture = SutFixture(listOf(HttpStatusCode.OK to fullPage))

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        assertEquals(500, fixture.deletions.size)
    }

    @Test
    fun `should fail without deleting anything when the search is refused`() = runUnitTest {
        given()
        val fixture = SutFixture(listOf(HttpStatusCode.Unauthorized to error("ERR_UNAUTHORIZED")))

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isFailure)
        assertTrue(fixture.deletions.isEmpty())
    }

    @Test
    fun `should fail when the deletion is refused`() = runUnitTest {
        given()
        val fixture = SutFixture(
            listOf(HttpStatusCode.OK to page(message(uid = 1, sentAt = CUTOFF - 1.days))),
            deleteStatus = HttpStatusCode.Forbidden,
        )

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isFailure)
        assertEquals(1, fixture.deletions.size)
    }

    private companion object {
        const val API_TOKEN = "hostinger-mail-token"
        const val MAILBOX_ID = "AC1a2b3c4d5e6f7g"
        val NOW: Instant = Instant.parse("2027-01-15T08:00:00Z")
        val CUTOFF: Instant = Instant.parse("2022-01-15T08:00:00Z")

        fun message(uid: Long, sentAt: Instant): String {
            return """{"uid":$uid,"path":"INBOX.Sent","date":"$sentAt","flags":["\\Seen"]}"""
        }

        fun page(vararg messages: String): String {
            return """{"data":[${messages.joinToString(",")}],""" +
                """"pagination":{"page":1,"perPage":100,"total":${messages.size},"totalPages":1}}"""
        }

        fun error(code: String): String {
            return """{"error":"Hostinger error $code","code":"$code","params":{}}"""
        }
    }
}
