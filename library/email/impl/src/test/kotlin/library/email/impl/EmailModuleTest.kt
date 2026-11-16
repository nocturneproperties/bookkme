package library.email.impl

import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import library.email.DeleteExpiredSentEmails
import library.email.EmailCodeIssuer
import library.email.EmailCodeRedeemer
import library.email.EmailSender
import library.email.impl.di.emailModule
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal class EmailModuleTest {

    private class SutFixture(environment: Map<String, String>) {
        private val koin = koinApplication {
            modules(
                module {
                    single<CacheClient<String>> { InMemoryCacheClient() }
                    single<MeterRegistry> { SimpleMeterRegistry() }
                },
                emailModule(SCOPE, environment::get),
            )
        }.koin
        val scope = koin.createScope("email", SCOPE)
    }

    @Test
    fun `should provide the sender and the code issuer and redeemer in the service's scope`() = runUnitTest {
        given()
        val fixture = SutFixture(CONFIGURED)

        whenn()
        val resolved = runCatching {
            listOf(
                fixture.scope.get<EmailSender>(),
                fixture.scope.get<EmailCodeIssuer>(),
                fixture.scope.get<EmailCodeRedeemer>(),
                fixture.scope.get<DeleteExpiredSentEmails>(),
            )
        }

        then()
        assertTrue(resolved.isSuccess) { resolved.toString() }
    }

    @Test
    fun `should log emails instead of sending them when the API token is the development token`() = runUnitTest {
        given()
        val fixture = SutFixture(
            CONFIGURED + ("APPLICATION_HOSTINGER_MAIL_TOKEN" to "development_token") -
                "APPLICATION_HOSTINGER_MAILBOX_ID",
        )

        whenn()
        val sender = fixture.scope.get<EmailSender>()

        then()
        assertTrue(sender is LoggingEmailSender) { sender.toString() }
    }

    @Test
    fun `should send emails through the Hostinger Mail API when the API token is a real token`() = runUnitTest {
        given()
        val fixture = SutFixture(CONFIGURED)

        whenn()
        val sender = fixture.scope.get<EmailSender>()

        then()
        assertTrue(sender is HostingerEmailSender) { sender.toString() }
    }

    @Test
    fun `should delete nothing from the mailbox when the API token is the development token`() = runUnitTest {
        given()
        val fixture = SutFixture(
            CONFIGURED + ("APPLICATION_HOSTINGER_MAIL_TOKEN" to "development_token") -
                "APPLICATION_HOSTINGER_MAILBOX_ID",
        )

        whenn()
        val cleanup = fixture.scope.get<DeleteExpiredSentEmails>()

        then()
        assertTrue(cleanup is NoSentEmailsToDelete) { cleanup.toString() }
    }

    @Test
    fun `should delete expired sent copies through the Hostinger Mail API when the API token is a real token`() =
        runUnitTest {
            given()
            val fixture = SutFixture(CONFIGURED)

            whenn()
            val cleanup = fixture.scope.get<DeleteExpiredSentEmails>()

            then()
            assertTrue(cleanup is HostingerDeleteExpiredSentEmails) { cleanup.toString() }
        }

    @Test
    fun `should name the missing API token variable`() = runUnitTest {
        given()
        val fixture = SutFixture(CONFIGURED - "APPLICATION_HOSTINGER_MAIL_TOKEN")

        whenn()
        val resolved = runCatching { fixture.scope.get<EmailSender>() }

        then()
        assertTrue(
            generateSequence(resolved.exceptionOrNull()) {
                it.cause
            }.any { it.message.orEmpty().contains("APPLICATION_HOSTINGER_MAIL_TOKEN") },
        ) { resolved.toString() }
    }

    @Test
    fun `should name the missing mailbox variable`() = runUnitTest {
        given()
        val fixture = SutFixture(CONFIGURED - "APPLICATION_HOSTINGER_MAILBOX_ID")

        whenn()
        val resolved = runCatching { fixture.scope.get<EmailSender>() }

        then()
        assertTrue(
            generateSequence(resolved.exceptionOrNull()) {
                it.cause
            }.any { it.message.orEmpty().contains("APPLICATION_HOSTINGER_MAILBOX_ID") },
        ) { resolved.toString() }
    }

    @Test
    fun `should name the missing sender name variable`() = runUnitTest {
        given()
        val fixture = SutFixture(CONFIGURED - "APPLICATION_EMAIL_SENDER_NAME")

        whenn()
        val resolved = runCatching { fixture.scope.get<EmailSender>() }

        then()
        assertTrue(
            generateSequence(resolved.exceptionOrNull()) {
                it.cause
            }.any { it.message.orEmpty().contains("APPLICATION_EMAIL_SENDER_NAME") },
        ) { resolved.toString() }
    }

    @Test
    fun `should name the missing code secret variable`() = runUnitTest {
        given()
        val fixture = SutFixture(CONFIGURED - "APPLICATION_EMAIL_CODE_SECRET")

        whenn()
        val resolved = runCatching { fixture.scope.get<EmailCodeIssuer>() }

        then()
        assertTrue(
            generateSequence(resolved.exceptionOrNull()) {
                it.cause
            }.any { it.message.orEmpty().contains("APPLICATION_EMAIL_CODE_SECRET") },
        ) { resolved.toString() }
    }

    @Test
    fun `should refuse a code secret too short to key the hash`() = runUnitTest {
        given()
        val fixture = SutFixture(CONFIGURED + ("APPLICATION_EMAIL_CODE_SECRET" to "short"))

        whenn()
        val resolved = runCatching { fixture.scope.get<EmailCodeRedeemer>() }

        then()
        assertTrue(
            generateSequence(resolved.exceptionOrNull()) {
                it.cause
            }.any { it.message.orEmpty().contains("APPLICATION_EMAIL_CODE_SECRET") },
        ) { resolved.toString() }
    }

    private companion object {
        val SCOPE = named("EmailModuleTestScope")
        val CONFIGURED = mapOf(
            "APPLICATION_HOSTINGER_MAIL_TOKEN" to "hostinger-mail-token",
            "APPLICATION_HOSTINGER_MAILBOX_ID" to "AC1a2b3c4d5e6f7g",
            "APPLICATION_EMAIL_SENDER_NAME" to "BookkMe",
            "APPLICATION_EMAIL_CODE_SECRET" to "0123456789abcdef0123456789abcdef-test-secret",
        )
    }
}
