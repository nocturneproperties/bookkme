package com.bookk.user.domain.impl.export

import com.bookk.business.domain.api.export.BusinessUserDataExport
import com.bookk.business.domain.api.export.ExportUserBusinessData
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.test.TestClock
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.client.api.export.ExportUserNotificationData
import com.bookk.notifications.client.api.export.NotificationsUserDataExport
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport
import com.bookk.server.appointments.client.api.export.ExportUserAppointments
import com.bookk.server.auth.client.export.AccountDataExport
import com.bookk.server.auth.client.export.ExportAccountData
import com.bookk.server.user.client.api.event.UserEvent
import com.bookk.user.domain.api.entity.LegalDocument
import com.bookk.user.domain.api.entity.LegalDocumentAcceptance
import com.bookk.user.domain.api.export.ContactMessageExport
import com.bookk.user.domain.api.export.ProfileExport
import com.bookk.user.domain.datasource.LegalDocumentDataSource
import com.bookk.user.domain.datasource.UserDataExportDataSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class PersonalDataExportAssemblerTest {

    private class SutFixture {
        val userId = Uuid.random()
        val userDataExportDataSource = mockk<UserDataExportDataSource>()
        val legalDocumentDataSource = mockk<LegalDocumentDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val exportAccountData = mockk<ExportAccountData>()
        val exportUserBusinessData = mockk<ExportUserBusinessData>()
        val exportUserAppointments = mockk<ExportUserAppointments>()
        val exportUserNotificationData = mockk<ExportUserNotificationData>()
        val eventProducer = mockk<StandardEventProducer>()
        val clock = TestClock()
        val sentEvents = mutableListOf<UserEvent.DataExportReady>()
        val readInTransaction = mutableListOf<Boolean>()
        val profile = ProfileExport.stub(id = userId)
        val account = AccountDataExport.stub(passkeys = listOf(AccountDataExport.PasskeyExport.stub()))
        val businesses = BusinessUserDataExport.stub(clientRecords = listOf(BusinessUserDataExport.ClientRecordExport.stub()))
        val appointments = AppointmentsUserDataExport.stub(appointments = listOf(AppointmentsUserDataExport.AppointmentExport.stub()))
        val notifications = NotificationsUserDataExport.stub(email = "olena@example.com")
        val sut = PersonalDataExportAssembler(
            userDataExportDataSource,
            legalDocumentDataSource,
            transactionManager,
            exportAccountData,
            exportUserBusinessData,
            exportUserAppointments,
            exportUserNotificationData,
            eventProducer,
            clock,
        )

        init {
            transactionManager.mockTransaction()
            coEvery { userDataExportDataSource.getProfile(userId) } coAnswers {
                readInTransaction += transactionManager.isInTransaction()
                profile
            }
            coEvery { userDataExportDataSource.getContactMessages(userId) } returns listOf(ContactMessageExport.stub())
            coEvery { legalDocumentDataSource.getAcceptances(userId) } returns listOf(
                LegalDocumentAcceptance(LegalDocument.PRIVACY_POLICY, "2026-11", Instant.fromEpochMilliseconds(1_000)),
            )
            coEvery { exportAccountData(userId) } returns Result.success(account)
            coEvery { exportUserBusinessData(userId) } returns Result.success(businesses)
            coEvery { exportUserAppointments(userId) } returns Result.success(appointments)
            coEvery { exportUserNotificationData(userId) } returns Result.success(notifications)
            coEvery { eventProducer.send(any<UserEvent.DataExportReady>(), any()) } coAnswers { sentEvents += firstArg<UserEvent.DataExportReady>() }
        }
    }

    @Test
    fun `should gather the user's data from every service into one JSON document`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val json = fixture.sut.assemble(fixture.userId)

        then()
        assertEquals(
            PersonalDataExport(
                formatVersion = PersonalDataExport.FORMAT_VERSION,
                generatedAt = fixture.clock.now,
                userId = fixture.userId,
                profile = fixture.profile,
                contactMessages = listOf(ContactMessageExport.stub()),
                legalAcceptances = listOf(PersonalDataExport.LegalAcceptanceExport("PRIVACY_POLICY", "2026-11", Instant.fromEpochMilliseconds(1_000))),
                account = fixture.account,
                businesses = fixture.businesses,
                appointments = fixture.appointments,
                notifications = fixture.notifications,
            ),
            Json.decodeFromString<PersonalDataExport>(json),
        )
    }

    @Test
    fun `should name the sections of the document plainly`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val json = fixture.sut.assemble(fixture.userId)

        then()
        assertEquals(
            setOf("formatVersion", "generatedAt", "userId", "profile", "contactMessages", "legalAcceptances", "account", "businesses", "appointments", "notifications"),
            Json.parseToJsonElement(json).jsonObject.keys,
        )
    }

    @Test
    fun `should read the user's own data inside a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut.assemble(fixture.userId)

        then()
        assertEquals(listOf(true), fixture.readInTransaction)
    }

    @Test
    fun `should fail for a user without a profile`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.userDataExportDataSource.getProfile(fixture.userId) } returns null

        whenn()
        val result = runSuspendCatching { fixture.sut.assemble(fixture.userId) }

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should fail when any service can't hand over its part`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.exportUserAppointments(fixture.userId) } returns Result.failure(IllegalStateException("appointments unreachable"))

        whenn()
        val result = runSuspendCatching { fixture.sut.assemble(fixture.userId) }

        then()
        assertTrue(result.isFailure)
    }

    @Test
    fun `should tell the user their export is ready and until when`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val expiresAt = Instant.fromEpochMilliseconds(5_000)

        whenn()
        fixture.sut.announceReady(fixture.userId, expiresAt)

        then()
        assertEquals(listOf(fixture.userId to expiresAt), fixture.sentEvents.map { it.userId to it.expiresAt })
    }

    @Test
    fun `should ask the other services for their parts at the same time`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val inFlight = AtomicInteger()
        val mostAtOnce = AtomicInteger()
        suspend fun <T> concurrently(part: T): Result<T> {
            mostAtOnce.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            delay(100)
            inFlight.decrementAndGet()
            return Result.success(part)
        }
        coEvery { fixture.exportAccountData(fixture.userId) } coAnswers { concurrently(fixture.account) }
        coEvery { fixture.exportUserBusinessData(fixture.userId) } coAnswers { concurrently(fixture.businesses) }
        coEvery { fixture.exportUserAppointments(fixture.userId) } coAnswers { concurrently(fixture.appointments) }
        coEvery { fixture.exportUserNotificationData(fixture.userId) } coAnswers { concurrently(fixture.notifications) }

        whenn()
        fixture.sut.assemble(fixture.userId)

        then()
        assertEquals(4, mostAtOnce.get())
    }
}
