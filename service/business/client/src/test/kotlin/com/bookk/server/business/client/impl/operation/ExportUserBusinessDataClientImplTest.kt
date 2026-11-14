package com.bookk.server.business.client.impl.operation

import com.bookk.business.domain.api.export.BusinessUserDataExport
import com.bookk.core.client.internalHttpClient
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ExportUserBusinessDataClientImplTest {

    private class SutFixture(status: HttpStatusCode = HttpStatusCode.OK) {
        val export = BusinessUserDataExport.stub(
            ownedBusiness = BusinessUserDataExport.OwnedBusinessExport.stub(),
            employments = listOf(
                BusinessUserDataExport.EmploymentExport.stub(suspendedAt = Instant.fromEpochMilliseconds(1_000)),
            ),
            clientRecords = listOf(BusinessUserDataExport.ClientRecordExport.stub(description = "prefers mornings")),
        )
        val requestedPaths = mutableListOf<String>()
        val sut = ExportUserBusinessDataClientImpl(
            internalHttpClient(
                engineFactory = MockEngine,
                hostnameVariable = "SERVICE_HOSTNAME",
                clientTag = "user",
                environment = { "business.service" },
            ) {
                addHandler { request ->
                    requestedPaths += request.url.encodedPath
                    respond(
                        content = ProtoBuf.encodeToByteArray(export),
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.ProtoBuf.toString()),
                    )
                }
            },
        )
    }

    @Test
    fun `should fetch the user's business data from business's internal export route`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()

        whenn()
        val result = fixture.sut(userId)

        then()
        assertEquals(fixture.export, result.getOrNull())
        assertEquals(listOf("/api/internal/export/business/$userId"), fixture.requestedPaths)
    }

    @Test
    fun `should fail when business can't export the data`() = runUnitTest {
        given()
        val fixture = SutFixture(status = HttpStatusCode.NotFound)

        whenn()
        val result = fixture.sut(Uuid.random())

        then()
        assertTrue(result.isFailure)
    }
}
