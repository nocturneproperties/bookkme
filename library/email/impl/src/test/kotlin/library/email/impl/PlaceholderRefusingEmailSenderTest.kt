package library.email.impl

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.email.EmailMessage
import library.email.EmailSendError
import library.email.EmailSender
import library.email.PlaceholderEmail
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class PlaceholderRefusingEmailSenderTest {

    private class SutFixture {
        val delegate = mockk<EmailSender>()
        val sut = PlaceholderRefusingEmailSender(delegate)
    }

    @Test
    fun `should refuse a placeholder address as undeliverable without sending it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val message = EmailMessage.stub(to = PlaceholderEmail.forTelegramUser(123456789))

        whenn()
        val result = fixture.sut.send(message)

        then()
        assertTrue(result.exceptionOrNull() is EmailSendError.Undeliverable) { result.toString() }
        coVerify(exactly = 0) { fixture.delegate.send(any()) }
    }

    @Test
    fun `should refuse a demo account placeholder address as undeliverable without sending it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val message = EmailMessage.stub(to = PlaceholderEmail.forDemoUser(Uuid.random()))

        whenn()
        val result = fixture.sut.send(message)

        then()
        assertTrue(result.exceptionOrNull() is EmailSendError.Undeliverable) { result.toString() }
        coVerify(exactly = 0) { fixture.delegate.send(any()) }
    }

    @Test
    fun `should refuse a placeholder address written in another case`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val message = EmailMessage.stub(to = " 42@TelegramUndeliverable.ORG ")

        whenn()
        val result = fixture.sut.send(message)

        then()
        assertTrue(result.exceptionOrNull() is EmailSendError.Undeliverable) { result.toString() }
        coVerify(exactly = 0) { fixture.delegate.send(any()) }
    }

    @Test
    fun `should refuse an address on a subdomain of the placeholder domain`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val message = EmailMessage.stub(to = "someone@mail.telegramundeliverable.org")

        whenn()
        val result = fixture.sut.send(message)

        then()
        assertTrue(result.exceptionOrNull() is EmailSendError.Undeliverable) { result.toString() }
        coVerify(exactly = 0) { fixture.delegate.send(any()) }
    }

    @Test
    fun `should pass a real address on to the delegate`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val message = EmailMessage.stub(to = "client@example.com")
        coEvery { fixture.delegate.send(message) } returns Result.success(Unit)

        whenn()
        val result = fixture.sut.send(message)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 1) { fixture.delegate.send(message) }
    }

    @Test
    fun `should not treat a subdomain of the placeholder domain as a placeholder`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val message = EmailMessage.stub(to = "someone@mail.telegramundeliverable.org.example.com")
        coEvery { fixture.delegate.send(message) } returns Result.success(Unit)

        whenn()
        val result = fixture.sut.send(message)

        then()
        assertTrue(result.isSuccess) { result.toString() }
    }
}
