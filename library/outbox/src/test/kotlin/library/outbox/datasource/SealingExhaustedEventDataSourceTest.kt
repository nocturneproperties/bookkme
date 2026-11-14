package library.outbox.datasource

import com.bookk.core.data.eventstreaming.DataSubject
import com.bookk.core.data.eventstreaming.DltEvent
import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.data.eventstreaming.Parked
import com.bookk.core.data.eventstreaming.ParkedPayload
import com.bookk.core.data.eventstreaming.impl.PersonalTestEvent
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import library.outbox.personal.MarkingPersonalDataCipher
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class SealingExhaustedEventDataSourceTest {

    private class SutFixture {
        val rows = mockk<ExhaustedEventRowDataSource>()
        val cipher = MarkingPersonalDataCipher()
        val sut = SealingExhaustedEventDataSource(rows, cipher)
        val saved = slot<DltEvent>()
        val savedEncrypted = slot<Boolean>()

        fun acceptSaves() {
            coEvery { rows.save(capture(saved), capture(savedEncrypted)) } just Runs
        }

        fun lockReturns(vararg locked: ExhaustedEventRow) {
            coEvery { rows.lockReplayRequested(LIMIT) } returns locked.toList()
        }
    }

    private fun personalEvent(userId: Uuid = Uuid.random()): PersonalTestEvent {
        return PersonalTestEvent(userId = userId, name = "Alice", topic = TOPIC)
    }

    private fun row(payload: ByteArray, personalDataEncrypted: Boolean): ExhaustedEventRow {
        return ExhaustedEventRow(
            id = Uuid.random(),
            event = DltEvent.stub(originalTopic = TOPIC, payload = payload),
            personalDataEncrypted = personalDataEncrypted,
        )
    }

    @Test
    fun `should store a personal event with its whole payload sealed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.acceptSaves()
        val event = personalEvent()
        val plaintext = byteArrayOf(1, 2, 3)

        whenn()
        val parked = fixture.sut.save(event, DltEvent.stub(originalTopic = TOPIC, payload = plaintext))

        then()
        assertEquals(Parked.Stored, parked)
        assertTrue(fixture.savedEncrypted.captured)
        assertArrayEquals(plaintext, MarkingPersonalDataCipher.plaintextOf(fixture.saved.captured.payload))
    }

    @Test
    fun `should store an event without personal data as plaintext`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.acceptSaves()
        val dltEvent = DltEvent.stub(originalTopic = TOPIC, payload = byteArrayOf(1, 2, 3))

        whenn()
        val parked = fixture.sut.save(mockk<EventStreaming.Event<String>>(), dltEvent)

        then()
        assertEquals(Parked.Stored, parked)
        assertFalse(fixture.savedEncrypted.captured)
        assertEquals(dltEvent, fixture.saved.captured)
    }

    @Test
    fun `should not store a personal event whose data subject was erased`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.cipher.erase(DataSubject.User(userId))

        whenn()
        val parked = fixture.sut.save(personalEvent(userId), DltEvent.stub(originalTopic = TOPIC))

        then()
        assertEquals(Parked.SubjectErased, parked)
        coVerify(exactly = 0) { fixture.rows.save(any(), any()) }
    }

    @Test
    fun `should open a sealed parked payload`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = personalEvent()
        val plaintext = byteArrayOf(1, 2, 3)
        val sealed = row(MarkingPersonalDataCipher.sealed(event.dataSubject, plaintext), personalDataEncrypted = true)
        fixture.lockReturns(sealed)

        whenn()
        val locked = fixture.sut.lockReplayRequested(LIMIT).single()

        then()
        assertEquals(sealed.id, locked.id)
        assertEquals(TOPIC, locked.originalTopic)
        assertEquals(ParkedPayload.Readable(plaintext), locked.payload)
    }

    @Test
    fun `should read a payload parked as plaintext as it was stored`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val plaintext = byteArrayOf(1, 2, 3)
        fixture.lockReturns(row(plaintext, personalDataEncrypted = false))

        whenn()
        val locked = fixture.sut.lockReplayRequested(LIMIT).single()

        then()
        assertEquals(ParkedPayload.Readable(plaintext), locked.payload)
    }

    @Test
    fun `should report a parked payload whose data subject was erased`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val sealed = MarkingPersonalDataCipher.sealed(DataSubject.User(userId), byteArrayOf(1, 2, 3))
        fixture.lockReturns(row(sealed, personalDataEncrypted = true))
        fixture.cipher.erase(DataSubject.User(userId))

        whenn()
        val locked = fixture.sut.lockReplayRequested(LIMIT).single()

        then()
        assertEquals(ParkedPayload.SubjectErased, locked.payload)
    }

    @Test
    fun `should report a sealed parked payload that cannot be opened`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.lockReturns(row(byteArrayOf(0x7f, 0x7f, 0x7f), personalDataEncrypted = true))

        whenn()
        val locked = fixture.sut.lockReplayRequested(LIMIT).single()

        then()
        assertTrue(locked.payload is ParkedPayload.Unreadable)
    }

    @Test
    fun `should request a replay of every stored row`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.rows.requestReplayOfAll() } just Runs

        whenn()
        fixture.sut.requestReplayOfAll()

        then()
        coVerify(exactly = 1) { fixture.rows.requestReplayOfAll() }
    }

    @Test
    fun `should delete the stored rows`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ids = listOf(Uuid.random())
        coEvery { fixture.rows.delete(ids) } just Runs

        whenn()
        fixture.sut.delete(ids)

        then()
        coVerify(exactly = 1) { fixture.rows.delete(ids) }
    }

    @Test
    fun `should clear the replay request of the stored rows`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val ids = listOf(Uuid.random())
        coEvery { fixture.rows.clearReplayRequest(ids) } just Runs

        whenn()
        fixture.sut.clearReplayRequest(ids)

        then()
        coVerify(exactly = 1) { fixture.rows.clearReplayRequest(ids) }
    }

    @Test
    fun `should count the stored rows by original topic`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.rows.countByOriginalTopic() } returns mapOf(TOPIC to 2L)

        whenn()
        val counts = fixture.sut.countByOriginalTopic()

        then()
        assertEquals(mapOf(TOPIC to 2L), counts)
    }

    @Test
    fun `should delete the rows parked before the cutoff`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val cutoff = Instant.fromEpochMilliseconds(0)
        coEvery { fixture.rows.deleteParkedBefore(cutoff) } returns 3

        whenn()
        val deleted = fixture.sut.deleteParkedBefore(cutoff)

        then()
        assertEquals(3, deleted)
    }

    private companion object {
        const val TOPIC = "sealing.topic"
        const val LIMIT = 10
    }
}
