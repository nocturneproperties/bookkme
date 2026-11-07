package com.bookk.notifications.client.impl

import com.bookk.core.client.internalHttpClient
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.client.api.export.NotificationsUserDataExport
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
import kotlin.uuid.Uuid

internal class ExportUserNotificationDataClientImplTest {

    private class SutFixture(status: HttpStatusCode = HttpStatusCode.OK) {
        val export = NotificationsUserDataExport.stub(
            settings = NotificationsUserDataExport.SettingsExport(true, listOf(NotificationsUserDataExport.ChannelExport("PUSH_NOTIFICATIONS", enabled = true))),
            devices = listOf(NotificationsUserDataExport.DeviceExport("UK", pushNotificationsRegistered = true)),
            email = "olena@example.com",
        )
        val requestedPaths = mutableListOf<String>()
        val sut = ExportUserNotificationDataClientImpl(
            internalHttpClient(
                engineFactory = MockEngine,
                hostnameVariable = "SERVICE_HOSTNAME",
                clientTag = "user",
                environment = { "notifications.service" },
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
    fun `should fetch the user's notification data from notifications' internal export route`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()

        whenn()
        val result = fixture.sut(userId)

        then()
        assertEquals(fixture.export, result.getOrNull())
        assertEquals(listOf("/api/internal/export/notifications/$userId"), fixture.requestedPaths)
    }

    @Test
    fun `should fail when notifications can't export the data`() = runUnitTest {
        given()
        val fixture = SutFixture(status = HttpStatusCode.NotFound)

        whenn()
        val result = fixture.sut(Uuid.random())

        then()
        assertTrue(result.isFailure)
    }
}
