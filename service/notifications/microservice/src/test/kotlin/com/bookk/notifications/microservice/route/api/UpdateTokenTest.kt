package com.bookk.notifications.microservice.route.api

import com.bookk.core.domain.entity.Error
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.api.UpdatePushNotificationToken
import com.bookk.notifications.domain.api.entity.Device
import com.bookk.notifications.microservice.route.NotificationsRouting.Api
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.client.call.body
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

internal class UpdateTokenTest {

    @Test
    fun `should update push notification token successfully`() = routeTest {
        given()
        val useCase: UpdatePushNotificationToken = mockk()
        val deviceUuid = Uuid.random()
        val token = "fcm-token-123"
        val device = Device.stub(deviceId = deviceUuid, notificationToken = token)
        coEvery { useCase.invoke(deviceUuid, token) } returns Result.success(device)
        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { context ->
                            context.principal(AppPrincipal(Uuid.random(), Uuid.random(), Uuid.random()))
                        }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { notificationToken() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(Api.Notification.Token(deviceUuid = deviceUuid)) {
            setBody(UpdateTokenRequest(token))
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(device, response.body<Device>())
    }

    @Test
    fun `should return not found when device does not exist`() = routeTest {
        given()
        val useCase: UpdatePushNotificationToken = mockk()
        val deviceUuid = Uuid.random()
        coEvery { useCase.invoke(deviceUuid, any()) } returns Result.failure(Error.NotFound())
        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { context ->
                            context.principal(AppPrincipal(Uuid.random(), Uuid.random(), Uuid.random()))
                        }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { notificationToken() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(Api.Notification.Token(deviceUuid = deviceUuid)) {
            setBody(UpdateTokenRequest("token"))
        }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return unauthorized when updating token without authentication`() = routeTest {
        given()
        val useCase: UpdatePushNotificationToken = mockk()
        setupApplication(
            extension = {
                install(Authentication) {
                    bearer { authenticate { null } }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { notificationToken() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(Api.Notification.Token(deviceUuid = Uuid.random())) {
            setBody(UpdateTokenRequest("token"))
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
