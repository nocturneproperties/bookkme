package library.export.route

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.acceptEncodingItems
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import library.export.DataExportDocument
import java.util.zip.GZIPInputStream

suspend fun ApplicationCall.respondDataExport(document: DataExportDocument, fileName: String) {
    response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, fileName).toString(),
    )
    response.header(HttpHeaders.CacheControl, "no-store")
    response.header(HttpHeaders.Vary, HttpHeaders.AcceptEncoding)
    if (acceptsGzip()) {
        response.header(HttpHeaders.ContentEncoding, GZIP)
        respondBytes(document.gzippedJson, ContentType.Application.Json)
    } else {
        respondBytes(
            GZIPInputStream(document.gzippedJson.inputStream()).use {
                it.readBytes()
            },
            ContentType.Application.Json,
        )
    }
}

private fun ApplicationCall.acceptsGzip(): Boolean = request.acceptEncodingItems().any {
    (
        it.value == GZIP ||
            it.value == "*"
        ) &&
        it.quality > 0.0
}

private const val GZIP = "gzip"
