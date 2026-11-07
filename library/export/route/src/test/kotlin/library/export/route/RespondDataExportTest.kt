package library.export.route

import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.get
import library.export.DataExportDocument
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

internal class RespondDataExportTest {

    private val json = """{"profile":{"name":"Olena"}}"""
    private val document = DataExportDocument(gzip(json))

    private fun io.ktor.server.testing.ApplicationTestBuilder.serveDocument() = setupApplication(
        diModule = module { },
        routeUnderTest = { get("/export/download") { call.respondDataExport(document, fileName = "bookk-personal-data.json") } },
    )

    @Test
    fun `should send the stored gzip bytes to a client that accepts gzip`() = routeTest {
        given()
        serveDocument()

        whenn()
        val response = createTestClient().get("/export/download") { header(HttpHeaders.AcceptEncoding, "gzip, deflate") }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
        assertArrayEquals(document.gzippedJson, response.bodyAsBytes())
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
    }

    @Test
    fun `should send plain JSON to a client that doesn't accept gzip`() = routeTest {
        given()
        serveDocument()

        whenn()
        val response = createTestClient().get("/export/download")

        then()
        assertNull(response.headers[HttpHeaders.ContentEncoding])
        assertEquals(json, response.bodyAsText())
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
    }

    @Test
    fun `should send plain JSON to a client that refuses gzip`() = routeTest {
        given()
        serveDocument()

        whenn()
        val response = createTestClient().get("/export/download") { header(HttpHeaders.AcceptEncoding, "gzip;q=0, identity") }

        then()
        assertNull(response.headers[HttpHeaders.ContentEncoding])
        assertEquals(json, response.bodyAsText())
    }

    @Test
    fun `should offer the document as a download that no cache keeps`() = routeTest {
        given()
        serveDocument()

        whenn()
        val response = createTestClient().get("/export/download")

        then()
        assertEquals("attachment; filename=bookk-personal-data.json", response.headers[HttpHeaders.ContentDisposition])
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals(HttpHeaders.AcceptEncoding, response.headers[HttpHeaders.Vary])
    }

    private fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { bytes ->
        GZIPOutputStream(bytes).use { it.write(text.encodeToByteArray()) }
    }.toByteArray()
}
