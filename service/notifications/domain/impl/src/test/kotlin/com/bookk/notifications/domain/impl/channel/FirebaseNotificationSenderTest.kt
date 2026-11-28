package com.bookk.notifications.domain.impl.channel

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.api.entity.Device
import com.bookk.notifications.domain.datasource.DeviceDataSource
import com.bookk.notifications.domain.impl.notification.EmailNotification
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import com.bookk.notifications.domain.impl.notification.NotificationType
import com.bookk.notifications.domain.impl.notification.PushNotification
import com.bookk.notifications.domain.impl.notification.PushSubject
import com.bookk.notifications.domain.impl.notification.TextNotification
import com.google.api.client.json.gson.GsonFactory
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class FirebaseNotificationSenderTest {

    private class SutFixture {
        val transactionManager = mockk<TransactionManager>()
        val deviceDataSource = mockk<DeviceDataSource>()
        val firebaseMessaging = mockk<FirebaseMessaging>()
        val sut = FirebaseNotificationSender(transactionManager, deviceDataSource, firebaseMessaging)
    }

    private fun notificationParams(subject: PushSubject? = null): NotificationParameters {
        return NotificationParameters(
            type = NotificationType.APPOINTMENT,
            push = { language ->
                PushNotification(
                    title = "Title-${language.name}",
                    body = "Subtitle-${language.name}",
                    subject = subject,
                )
            },
            email = { EmailNotification(subject = "Subject", body = "Body") },
            text = { TextNotification(text = "Text") },
        )
    }

    private fun Message.json(): JsonObject {
        return Json.parseToJsonElement(GsonFactory.getDefaultInstance().toString(this)).jsonObject
    }

    @Test
    fun `should tag the push with its subject key and data`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val device = Device.stub(userId = userId, notificationToken = "fcm-token-abc")
        val subject = PushSubject(key = "appointment_request:42", data = mapOf("appointmentRequestId" to "42"))
        val sent = slot<Message>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.getByUserId(userId) } returns listOf(device)
            every { firebaseMessaging.send(capture(sent)) } returns "message-id"
        }

        whenn()
        val result = fixture.sut.send(userId, notificationParams(subject))

        then()
        assertTrue(result.isSuccess)
        val message = sent.captured.json()
        val androidTag = message["android"]?.jsonObject?.get("notification")?.jsonObject?.get("tag")
        val collapseId = message["apns"]?.jsonObject?.get("headers")?.jsonObject?.get("apns-collapse-id")
        val requestId = message["data"]?.jsonObject?.get("appointmentRequestId")
        assertEquals("appointment_request:42", androidTag?.jsonPrimitive?.content)
        assertEquals("appointment_request:42", collapseId?.jsonPrimitive?.content)
        assertEquals("42", requestId?.jsonPrimitive?.content)
        assertEquals("Title-EN", message["notification"]?.jsonObject?.get("title")?.jsonPrimitive?.content)
    }

    @Test
    fun `should send a push without a subject with only its title and body`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val device = Device.stub(userId = userId, notificationToken = "fcm-token-abc")
        val sent = slot<Message>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.getByUserId(userId) } returns listOf(device)
            every { firebaseMessaging.send(capture(sent)) } returns "message-id"
        }

        whenn()
        val result = fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(result.isSuccess)
        val message = sent.captured.json()
        assertNull(message["android"])
        assertNull(message["apns"])
        assertNull(message["data"])
        assertEquals("Subtitle-EN", message["notification"]?.jsonObject?.get("body")?.jsonPrimitive?.content)
    }

    @Test
    fun `should send push notification to all devices with tokens`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val device = Device.stub(userId = userId, notificationToken = "fcm-token-abc")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.getByUserId(userId) } returns listOf(device)
            every { firebaseMessaging.send(any()) } returns "message-id"
        }

        whenn()
        val result = fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(result.isSuccess)
        verify(exactly = 1) { fixture.firebaseMessaging.send(any()) }
    }

    @Test
    fun `should render push content using each device's own language`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val englishDevice = Device.stub(userId = userId, notificationToken = "fcm-token-en", language = Language.EN)
        val ukrainianDevice = Device.stub(userId = userId, notificationToken = "fcm-token-uk", language = Language.UK)
        val pushRenderer = mockk<(Language) -> PushNotification>()
        val params = NotificationParameters(
            type = NotificationType.APPOINTMENT,
            push = pushRenderer,
            email = { EmailNotification(subject = "Subject", body = "Body") },
            text = { TextNotification(text = "Text") },
        )
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.getByUserId(userId) } returns listOf(englishDevice, ukrainianDevice)
            every { pushRenderer(Language.EN) } returns PushNotification(title = "Title-EN", body = "Subtitle-EN")
            every { pushRenderer(Language.UK) } returns PushNotification(title = "Title-UK", body = "Subtitle-UK")
            every { firebaseMessaging.send(any()) } returns "message-id"
        }

        whenn()
        val result = fixture.sut.send(userId, params)

        then()
        assertTrue(result.isSuccess)
        verify(exactly = 1) { pushRenderer(Language.EN) }
        verify(exactly = 1) { pushRenderer(Language.UK) }
        verify(exactly = 2) { fixture.firebaseMessaging.send(any()) }
    }

    @Test
    fun `should read devices inside a transaction and push outside it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val device = Device.stub(userId = userId, notificationToken = "fcm-token-abc")
        var readInTransaction = false
        var sentInTransaction = true
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.getByUserId(userId) } coAnswers {
                readInTransaction = transactionManager.isInTransaction()
                listOf(device)
            }
            coEvery { firebaseMessaging.send(any()) } coAnswers {
                sentInTransaction = transactionManager.isInTransaction()
                "message-id"
            }
        }

        whenn()
        fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(readInTransaction)
        assertFalse(sentInTransaction)
    }

    @Test
    fun `should clear an unregistered token inside a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val device = Device.stub(userId = userId, notificationToken = "stale-token")
        val exception = mockk<FirebaseMessagingException>()
        var clearedInTransaction = false
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.getByUserId(userId) } returns listOf(device)
            coEvery { deviceDataSource.updateToken(device.authId, device.deviceUuid, null) } coAnswers {
                clearedInTransaction = transactionManager.isInTransaction()
                device.copy(notificationToken = null)
            }
            every { firebaseMessaging.send(any()) } throws exception
            every { exception.messagingErrorCode } returns MessagingErrorCode.UNREGISTERED
        }

        whenn()
        fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(clearedInTransaction)
    }

    @Test
    fun `should skip devices without notification tokens`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val device = Device.stub(userId = userId, notificationToken = null)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.getByUserId(userId) } returns listOf(device)
        }

        whenn()
        val result = fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(result.isSuccess)
        verify(exactly = 0) { fixture.firebaseMessaging.send(any()) }
    }

    @Test
    fun `should clear token when firebase returns UNREGISTERED error`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val deviceId = Uuid.random()
        val device = Device.stub(deviceId = deviceId, userId = userId, notificationToken = "stale-token")
        val exception = mockk<FirebaseMessagingException>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.getByUserId(userId) } returns listOf(device)
            coEvery { deviceDataSource.updateToken(device.authId, deviceId, null) } returns
                device.copy(notificationToken = null)
            every { firebaseMessaging.send(any()) } throws exception
            every { exception.messagingErrorCode } returns MessagingErrorCode.UNREGISTERED
        }

        whenn()
        val result = fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.deviceDataSource.updateToken(device.authId, deviceId, null) }
    }

    @Test
    fun `should log error and not clear token for non-UNREGISTERED firebase exceptions`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val device = Device.stub(userId = userId, notificationToken = "fcm-token")
        val exception = mockk<FirebaseMessagingException>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.getByUserId(userId) } returns listOf(device)
            every { firebaseMessaging.send(any()) } throws exception
            every { exception.suppressed } returns arrayOf()
            every { exception.cause } returns null
            every { exception.stackTrace } returns arrayOf()
            every { exception.message } returns "message"
            every { exception.messagingErrorCode } returns MessagingErrorCode.INTERNAL
        }

        whenn()
        val result = fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.deviceDataSource.updateToken(any(), any(), any()) }
    }
}
