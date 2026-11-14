package library.email.impl

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.email.EmailContent
import library.email.EmailMessage
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

internal class LoggingEmailSenderTest {

    private class SutFixture {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val logger = (LoggerFactory.getLogger(LoggingEmailSender::class.java) as Logger).apply { addAppender(appender) }
        val sut = LoggingEmailSender(from = "no-reply@example.com")

        fun loggedText() = appender.list.joinToString("\n") { it.formattedMessage }

        fun detach() = logger.detachAppender(appender)
    }

    @Test
    fun `should log the recipient, subject and text body instead of sending the email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val message = EmailMessage.stub(
            to = "client@example.com",
            content = EmailContent.stub(subject = "Your code", textBody = "Your code is 123456"),
        )

        whenn()
        val result = try {
            fixture.sut.send(message)
        } finally {
            fixture.detach()
        }

        then()
        val log = fixture.loggedText()
        assertTrue(result.isSuccess) { result.toString() }
        assertTrue("client@example.com" in log && "Your code" in log && "Your code is 123456" in log) { log }
    }
}
