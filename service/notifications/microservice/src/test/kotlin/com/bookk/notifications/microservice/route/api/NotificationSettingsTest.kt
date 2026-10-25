package com.bookk.notifications.microservice.route.api

import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.api.GetNotificationSettings
import com.bookk.notifications.domain.api.UpdateNotificationSettings
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import com.bookk.notifications.domain.api.entity.NotificationChannelSettings
import com.bookk.notifications.domain.api.entity.NotificationSettings
import com.bookk.notifications.microservice.route.NotificationsRouting.Api
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class NotificationSettingsTest {

    @Test
    fun `should get notification settings successfully`() = routeTest {
        given()
        val useCase: GetNotificationSettings = mockk()
        val userId = Uuid.random()
        val settings = NotificationSettings.stub(
            userId = userId,
            channels = listOf(
                NotificationChannelSettings.stub(channel = CommunicationChannel.EMAIL, enabled = true),
                NotificationChannelSettings.stub(channel = CommunicationChannel.PUSH_NOTIFICATIONS, enabled = false),
                NotificationChannelSettings.stub(channel = CommunicationChannel.TELEGRAM, enabled = false),
            )
        )
        coEvery { useCase.invoke(userId) } returns Result.success(settings)
        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { context ->
                            context.principal(AppPrincipal(Uuid.random(), userId, Uuid.random()))
                        }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { notificationSettings() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(Api.Notification.Settings())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(settings, response.body<NotificationSettings>())
    }

    @Test
    fun `should return 500 when get notification settings fails`() = routeTest {
        given()
        val useCase: GetNotificationSettings = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId) } returns Result.failure(RuntimeException("db error"))
        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { context ->
                            context.principal(AppPrincipal(Uuid.random(), userId, Uuid.random()))
                        }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { notificationSettings() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(Api.Notification.Settings())

        then()
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `should return unauthorized when getting settings without authentication`() = routeTest {
        given()
        val useCase: GetNotificationSettings = mockk()
        setupApplication(
            extension = {
                install(Authentication) {
                    bearer { authenticate { null } }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { notificationSettings() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(Api.Notification.Settings())

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should update notification settings with channel enabled states successfully`() = routeTest {
        given()
        val useCase: UpdateNotificationSettings = mockk()
        val userId = Uuid.random()
        val channels = listOf(
            NotificationChannelSettings.stub(channel = CommunicationChannel.TELEGRAM, enabled = true),
            NotificationChannelSettings.stub(channel = CommunicationChannel.EMAIL, enabled = false),
            NotificationChannelSettings.stub(channel = CommunicationChannel.PUSH_NOTIFICATIONS, enabled = true),
        )
        val settings = NotificationSettings.stub(userId = userId, appointmentEnabled = false, channels = channels)
        coEvery { useCase.invoke(userId, any()) } returns Result.success(settings)
        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { context ->
                            context.principal(AppPrincipal(Uuid.random(), userId, Uuid.random()))
                        }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { notificationSettings() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(Api.Notification.Settings()) {
            setBody(NotificationSettings.Update(id = Uuid.random(), appointmentEnabled = false, channels = channels))
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(settings, response.body<NotificationSettings>())
    }

    @Test
    fun `should return 500 when update notification settings fails`() = routeTest {
        given()
        val useCase: UpdateNotificationSettings = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, any()) } returns Result.failure(RuntimeException("db error"))
        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { context ->
                            context.principal(AppPrincipal(Uuid.random(), userId, Uuid.random()))
                        }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { notificationSettings() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(Api.Notification.Settings()) {
            setBody(NotificationSettings.Update(id = Uuid.random(), appointmentEnabled = true, channels = emptyList()))
        }

        then()
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `should return unauthorized when updating settings without authentication`() = routeTest {
        given()
        val useCase: UpdateNotificationSettings = mockk()
        setupApplication(
            extension = {
                install(Authentication) {
                    bearer { authenticate { null } }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { notificationSettings() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(Api.Notification.Settings()) {
            setBody(NotificationSettings.Update(id = Uuid.random(), appointmentEnabled = true, channels = emptyList()))
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
