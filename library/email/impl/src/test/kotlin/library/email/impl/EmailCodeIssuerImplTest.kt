package library.email.impl

import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import library.email.EmailCodeError
import library.email.EmailContent
import library.email.EmailMessage
import library.email.EmailSendError
import library.email.EmailSender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TestTimeSource
import kotlin.uuid.Uuid

internal class EmailCodeIssuerImplTest {

    private class SutFixture {
        val timeSource = TestTimeSource()
        val start = Instant.parse("2026-11-07T10:00:00Z")
        val clock = object : Clock {
            private val startMark = timeSource.markNow()

            override fun now(): Instant = start + startMark.elapsedNow()
        }
        val store = EmailCodeStore(InMemoryCacheClient(timeSource = timeSource))
        val sender = mockk<EmailSender>()
        val sentMessages = mutableListOf<EmailMessage>()
        val renderedCodes = mutableListOf<String>()
        val ownerId = Uuid.random()
        val sut = EmailCodeIssuerImpl(store, sender, SecureRandom(), clock)

        init {
            coEvery { sender.send(any()) } answers {
                sentMessages += firstArg<EmailMessage>()
                Result.success(Unit)
            }
        }

        suspend fun issue(email: String = EMAIL) = sut.issue(email, PURPOSE, ownerId) { code ->
            renderedCodes += code
            EmailContent.stub(textBody = "Your code is $code")
        }
    }

    @Test
    fun `should email a six digit code to the address and say when it expires and when a new one can be sent`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.issue()

        then()
        val started = result.getOrThrow()
        assertEquals(fixture.start + EmailCodeIssuerImpl.CODE_LIFETIME, started.expiresAt)
        assertEquals(fixture.start + EmailCodeIssuerImpl.RESEND_COOLDOWN, started.resendAvailableAt)
        val code = fixture.renderedCodes.single()
        assertTrue(code.matches(Regex("\\d{6}"))) { code }
        val message = fixture.sentMessages.single()
        assertEquals(EMAIL, message.to)
        assertEquals("Your code is $code", message.content.textBody)
    }

    @Test
    fun `should store the session for its owner and purpose without the code itself`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val started = fixture.issue().getOrThrow()

        then()
        val session = assertNotNull(fixture.store.session(started.verificationId))
        val code = fixture.renderedCodes.single()
        assertEquals(fixture.ownerId, session.ownerId)
        assertEquals(EMAIL, session.email)
        assertEquals(PURPOSE, session.purpose)
        assertFalse(session.codeHash.contains(code))
        assertEquals(started.verificationId, fixture.store.latest(fixture.ownerId))
    }

    @Test
    fun `should refuse a second code to the same address within the cooldown without sending it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.issue().getOrThrow()
        fixture.timeSource += EmailCodeIssuerImpl.RESEND_COOLDOWN - 1.seconds

        whenn()
        val result = fixture.issue()

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.ResendTooSoon) { result.toString() }
        assertEquals(1, fixture.sentMessages.size)
    }

    @Test
    fun `should send a new code once the cooldown has passed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.issue().getOrThrow()
        fixture.timeSource += EmailCodeIssuerImpl.RESEND_COOLDOWN + 1.seconds

        whenn()
        val result = fixture.issue()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        assertEquals(2, fixture.sentMessages.size)
    }

    @Test
    fun `should not let one address's cooldown hold up another address`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.issue(EMAIL).getOrThrow()

        whenn()
        val result = fixture.issue("other@example.com")

        then()
        assertTrue(result.isSuccess) { result.toString() }
    }

    @Test
    fun `should refuse more than five codes to one address within an hour`() = runUnitTest {
        given()
        val fixture = SutFixture()
        repeat(EmailCodeIssuerImpl.MAX_SENDS_PER_WINDOW) {
            fixture.issue().getOrThrow()
            fixture.timeSource += EmailCodeIssuerImpl.RESEND_COOLDOWN + 1.seconds
        }

        whenn()
        val result = fixture.issue()

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.ResendTooSoon) { result.toString() }
        assertEquals(EmailCodeIssuerImpl.MAX_SENDS_PER_WINDOW, fixture.sentMessages.size)
    }

    @Test
    fun `should send to the address again once the hour has passed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        repeat(EmailCodeIssuerImpl.MAX_SENDS_PER_WINDOW) {
            fixture.issue().getOrThrow()
            fixture.timeSource += EmailCodeIssuerImpl.RESEND_COOLDOWN + 1.seconds
        }
        fixture.timeSource += EmailCodeIssuerImpl.SEND_LIMIT_WINDOW

        whenn()
        val result = fixture.issue()

        then()
        assertTrue(result.isSuccess) { result.toString() }
    }

    @Test
    fun `should pass an undeliverable address on and let the user send again at once`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.sender.send(any()) } returns Result.failure(EmailSendError.Undeliverable())
        val refused = fixture.issue()
        coEvery { fixture.sender.send(any()) } returns Result.success(Unit)

        whenn()
        val result = fixture.issue()

        then()
        assertTrue(refused.exceptionOrNull() is EmailSendError.Undeliverable) { refused.toString() }
        assertTrue(result.isSuccess) { result.toString() }
    }

    @Test
    fun `should keep the previous code current when sending the new one failed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = fixture.issue().getOrThrow()
        fixture.timeSource += EmailCodeIssuerImpl.RESEND_COOLDOWN + 1.seconds
        coEvery { fixture.sender.send(any()) } returns Result.failure(IllegalStateException("Postmark is down"))

        whenn()
        val result = fixture.issue()

        then()
        assertTrue(result.isFailure)
        assertEquals(first.verificationId, fixture.store.latest(fixture.ownerId))
    }

    @Test
    fun `should let the user send again at once after sending was cancelled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.sender.send(any()) } coAnswers { awaitCancellation() }
        val sending = launch { fixture.issue() }
        runCurrent()
        sending.cancelAndJoin()
        coEvery { fixture.sender.send(any()) } returns Result.success(Unit)

        whenn()
        val result = fixture.issue()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 2) { fixture.sender.send(any()) }
    }

    private companion object {
        const val EMAIL = "client@example.com"
        const val PURPOSE = "verify"
    }
}
