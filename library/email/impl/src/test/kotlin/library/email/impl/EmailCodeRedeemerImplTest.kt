package library.email.impl

import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import library.email.EmailCodeError
import library.email.EmailContent
import library.email.EmailSender
import library.email.VerifiedEmail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.uuid.Uuid

internal class EmailCodeRedeemerImplTest {

    private class IssuedCode(val verificationId: Uuid, val code: String)

    private class SutFixture {
        val timeSource = TestTimeSource()
        val hasher = EmailCodeHasher.fromSecret("0123456789abcdef0123456789abcdef-test-secret")
        val store = EmailCodeStore(InMemoryCacheClient(timeSource = timeSource), hasher)
        val sender = mockk<EmailSender>()
        val issuer = EmailCodeIssuerImpl(store, hasher, sender, SecureRandom(), Clock.System)
        val ownerId = Uuid.random()
        val sut = EmailCodeRedeemerImpl(store, hasher)
        var actionRuns = 0

        init {
            coEvery { sender.send(any()) } returns Result.success(Unit)
        }

        suspend fun issue(email: String = EMAIL): IssuedCode {
            var code = ""
            val started = issuer.issue(email, PURPOSE, ownerId) {
                code = it
                EmailContent.stub()
            }.getOrThrow()
            return IssuedCode(started.verificationId, code)
        }

        suspend fun redeem(
            issued: IssuedCode,
            code: String = issued.code,
            ownerId: Uuid = this.ownerId,
            action: suspend (VerifiedEmail) -> String = { "done" },
        ): Result<String> = sut.redeem(issued.verificationId, code, ownerId) {
            actionRuns++
            action(it)
        }
    }

    @Test
    fun `should run the action with the verified address and purpose for the right code`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val issued = fixture.issue()
        var verified: VerifiedEmail? = null

        whenn()
        val result = fixture.redeem(issued) {
            verified = it
            "done"
        }

        then()
        assertEquals("done", result.getOrNull())
        assertEquals(VerifiedEmail(email = EMAIL, purpose = PURPOSE), verified)
    }

    @Test
    fun `should reject a wrong code without running the action`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val issued = fixture.issue()

        whenn()
        val result = fixture.redeem(issued, code = wrongCode(issued.code))

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.Invalid) { result.toString() }
        assertEquals(0, fixture.actionRuns)
    }

    @Test
    fun `should reject the right code once it expired`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val issued = fixture.issue()
        fixture.timeSource += EmailCodeIssuerImpl.CODE_LIFETIME + 1.seconds

        whenn()
        val result = fixture.redeem(issued)

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.Expired) { result.toString() }
        assertEquals(0, fixture.actionRuns)
    }

    @Test
    fun `should reject a verification that was never started`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.redeem(IssuedCode(Uuid.random(), "123456"))

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.Expired) { result.toString() }
    }

    @Test
    fun `should reject the right code from someone other than the user it was sent for`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val issued = fixture.issue()

        whenn()
        val result = fixture.redeem(issued, ownerId = Uuid.random())

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.Expired) { result.toString() }
        assertEquals(0, fixture.actionRuns)
    }

    @Test
    fun `should not count another user's guesses against the owner's attempts`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val issued = fixture.issue()
        repeat(EmailCodeRedeemerImpl.MAX_ATTEMPTS) { fixture.redeem(issued, ownerId = Uuid.random()) }

        whenn()
        val result = fixture.redeem(issued)

        then()
        assertEquals("done", result.getOrNull())
    }

    @Test
    fun `should accept the right code on the last allowed attempt`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val issued = fixture.issue()
        repeat(EmailCodeRedeemerImpl.MAX_ATTEMPTS - 1) { fixture.redeem(issued, code = wrongCode(issued.code)) }

        whenn()
        val result = fixture.redeem(issued)

        then()
        assertEquals("done", result.getOrNull())
    }

    @Test
    fun `should refuse even the right code after five wrong guesses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val issued = fixture.issue()
        repeat(EmailCodeRedeemerImpl.MAX_ATTEMPTS) { fixture.redeem(issued, code = wrongCode(issued.code)) }

        whenn()
        val result = fixture.redeem(issued)

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.AttemptsExceeded) { result.toString() }
        assertEquals(0, fixture.actionRuns)
    }

    @Test
    fun `should end the verification once its attempts are used up`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val issued = fixture.issue()
        repeat(EmailCodeRedeemerImpl.MAX_ATTEMPTS + 1) { fixture.redeem(issued, code = wrongCode(issued.code)) }

        whenn()
        val result = fixture.redeem(issued)

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.Expired) { result.toString() }
    }

    @Test
    fun `should accept a code only once`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val issued = fixture.issue()
        fixture.redeem(issued).getOrThrow()

        whenn()
        val result = fixture.redeem(issued)

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.Expired) { result.toString() }
        assertEquals(1, fixture.actionRuns)
    }

    @Test
    fun `should let only one of two concurrent confirmations with the right code through`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val issued = fixture.issue()
        val firstMayFinish = CompletableDeferred<Unit>()
        val first = async {
            fixture.redeem(issued) {
                firstMayFinish.await()
                "first"
            }
        }
        runCurrent()

        whenn()
        val second = fixture.redeem(issued) { "second" }
        firstMayFinish.complete(Unit)

        then()
        assertEquals("first", first.await().getOrNull())
        assertTrue(second.exceptionOrNull() is EmailCodeError.Expired) { second.toString() }
    }

    @Test
    fun `should keep the code usable when the action failed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val issued = fixture.issue()
        val failed = fixture.redeem(issued) { throw IllegalStateException("user service is down") }

        whenn()
        val result = fixture.redeem(issued)

        then()
        assertTrue(failed.exceptionOrNull() is IllegalStateException) { failed.toString() }
        assertEquals("done", result.getOrNull())
    }

    @Test
    fun `should keep the code usable when the action was cancelled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val issued = fixture.issue()
        val confirming = launch { fixture.redeem(issued) { awaitCancellation() } }
        runCurrent()
        confirming.cancelAndJoin()

        whenn()
        val result = fixture.redeem(issued)

        then()
        assertEquals("done", result.getOrNull())
    }

    @Test
    fun `should reject an older code once a newer one was sent to the user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val older = fixture.issue(EMAIL)
        fixture.issue("other@example.com")

        whenn()
        val result = fixture.redeem(older)

        then()
        assertTrue(result.exceptionOrNull() is EmailCodeError.Expired) { result.toString() }
    }

    private companion object {
        const val EMAIL = "client@example.com"
        const val PURPOSE = "verify"

        fun wrongCode(code: String) = ((code.toInt() + 1) % 1_000_000).toString().padStart(EmailCodeIssuerImpl.CODE_LENGTH, '0')
    }
}
