package library.outbox.personal

import com.bookk.core.data.eventstreaming.DataSubject
import com.bookk.core.data.eventstreaming.PersonalEvent
import com.bookk.core.domain.entity.runCatchingUnlessCancelled
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class SubjectKeysPersonalDataCipherTest {

    private data class ClientEvent(
        val clientId: Uuid,
        val clientUserId: Uuid?,
        override val topic: String = TOPIC,
        override val idempotencyKey: String = Uuid.random().toString(),
    ) : PersonalEvent {
        override val dataSubject: DataSubject get() = DataSubject.Client(clientId, clientUserId)
    }

    private data class ProfileEvent(
        val userId: Uuid,
        override val topic: String = TOPIC,
        override val idempotencyKey: String = Uuid.random().toString(),
    ) : PersonalEvent {
        override val dataSubject: DataSubject get() = DataSubject.User(userId)
    }

    private class SutFixture(clientKey: ClientKey = ClientKey.CLIENT_ID) {
        val subjectKeys = InMemorySubjectKeys()
        val missingKeys = RecordingMissingKeys()
        val sut = SubjectKeysPersonalDataCipher(subjectKeys, clientKey, missingKeys)

        suspend fun sealed(event: PersonalEvent): ByteArray {
            return requireNotNull(sut.seal(event, PAYLOAD))
        }
    }

    private fun clientEvent(clientUserId: Uuid? = Uuid.random()) = ClientEvent(
        clientId = Uuid.random(),
        clientUserId = clientUserId,
    )

    @Test
    fun `should open a sealed payload back to the original bytes`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val sealed = fixture.sealed(ProfileEvent(userId = Uuid.random()))

        whenn()
        val opened = fixture.sut.open(TOPIC, sealed)

        then()
        assertArrayEquals(PAYLOAD, opened)
    }

    @Test
    fun `should not store the payload readable in the sealed bytes`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val sealed = fixture.sealed(ProfileEvent(userId = Uuid.random()))

        then()
        assertFalse(sealed.contentEquals(PAYLOAD))
    }

    @Test
    fun `should seal a user's event under the user's key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = ProfileEvent(userId = Uuid.random())
        val sealed = fixture.sealed(event)

        whenn()
        fixture.subjectKeys.shred(event.userId)
        val opened = fixture.sut.open(TOPIC, sealed)

        then()
        assertNull(opened)
    }

    @Test
    fun `should seal a client's event under the client's key when clients are keyed by client`() = runUnitTest {
        given()
        val fixture = SutFixture(ClientKey.CLIENT_ID)
        val event = clientEvent()
        val sealed = fixture.sealed(event)

        whenn()
        fixture.subjectKeys.shred(requireNotNull(event.clientUserId))
        val openedAfterUserErasure = fixture.sut.open(TOPIC, sealed)
        fixture.subjectKeys.shred(event.clientId)
        val openedAfterClientErasure = fixture.sut.open(TOPIC, sealed)

        then()
        assertArrayEquals(PAYLOAD, openedAfterUserErasure)
        assertNull(openedAfterClientErasure)
    }

    @Test
    fun `should seal a client's event under the linked user's key when clients are keyed by user`() = runUnitTest {
        given()
        val fixture = SutFixture(ClientKey.LINKED_USER_ID)
        val event = clientEvent()
        val sealed = fixture.sealed(event)

        whenn()
        fixture.subjectKeys.shred(requireNotNull(event.clientUserId))
        val opened = fixture.sut.open(TOPIC, sealed)

        then()
        assertNull(opened)
    }

    @Test
    fun `should seal the event of a client without an account under the client's key when keyed by user`() =
        runUnitTest {
            given()
            val fixture = SutFixture(ClientKey.LINKED_USER_ID)
            val event = clientEvent(clientUserId = null)
            val sealed = fixture.sealed(event)

            whenn()
            fixture.subjectKeys.shred(event.clientId)
            val opened = fixture.sut.open(TOPIC, sealed)

            then()
            assertNull(opened)
        }

    @Test
    fun `should refuse to seal the event of an erased data subject`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = ProfileEvent(userId = Uuid.random())
        fixture.subjectKeys.shred(event.userId)

        whenn()
        val sealed = fixture.sut.seal(event, PAYLOAD)

        then()
        assertNull(sealed)
    }

    @Test
    fun `should drop a sealed payload without counting a missing key when its data subject was erased`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            val event = ProfileEvent(userId = Uuid.random())
            val sealed = fixture.sealed(event)
            fixture.subjectKeys.shred(event.userId)

            whenn()
            val opened = fixture.sut.open(TOPIC, sealed)

            then()
            assertNull(opened)
            assertEquals(emptyList<String>(), fixture.missingKeys.reported)
        }

    @Test
    fun `should drop a sealed payload and count the missing key when a key was lost`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = ProfileEvent(userId = Uuid.random())
        val sealed = fixture.sealed(event)
        fixture.subjectKeys.loseKey(event.userId)

        whenn()
        val opened = fixture.sut.open(TOPIC, sealed)

        then()
        assertNull(opened)
        assertEquals(listOf(TOPIC), fixture.missingKeys.reported)
    }

    @Test
    fun `should reject bytes that are not a sealed payload`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = runCatchingUnlessCancelled { fixture.sut.open(TOPIC, byteArrayOf(0x7f, 0x7f, 0x7f)) }

        then()
        assertTrue(result.exceptionOrNull() is UnreadableSealedPayload)
    }

    @Test
    fun `should reject a sealed payload whose ciphertext was tampered with`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val event = ProfileEvent(userId = Uuid.random())
        val sealed = fixture.sealed(event)
        fixture.subjectKeys.tamperWithCiphertextsOf(event.userId)

        whenn()
        val result = runCatchingUnlessCancelled { fixture.sut.open(TOPIC, sealed) }

        then()
        assertTrue(result.exceptionOrNull() is UnreadableSealedPayload)
    }

    private companion object {
        const val TOPIC = "personal.topic"
        val PAYLOAD = byteArrayOf(8, 1, 18, 3, -1, 0, 0x7f)
    }
}
