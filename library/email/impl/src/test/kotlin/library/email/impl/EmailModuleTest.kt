package library.email.impl

import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
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
                module { single<CacheClient<String>> { InMemoryCacheClient() } },
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
            listOf(fixture.scope.get<EmailSender>(), fixture.scope.get<EmailCodeIssuer>(), fixture.scope.get<EmailCodeRedeemer>())
        }

        then()
        assertTrue(resolved.isSuccess) { resolved.toString() }
    }

    @Test
    fun `should name the missing server token variable`() = runUnitTest {
        given()
        val fixture = SutFixture(CONFIGURED - "APPLICATION_POSTMARK_SERVER_TOKEN")

        whenn()
        val resolved = runCatching { fixture.scope.get<EmailSender>() }

        then()
        assertTrue(generateSequence(resolved.exceptionOrNull()) { it.cause }.any { it.message.orEmpty().contains("APPLICATION_POSTMARK_SERVER_TOKEN") }) { resolved.toString() }
    }

    @Test
    fun `should name the missing sender address variable`() = runUnitTest {
        given()
        val fixture = SutFixture(CONFIGURED - "APPLICATION_EMAIL_FROM")

        whenn()
        val resolved = runCatching { fixture.scope.get<EmailSender>() }

        then()
        assertTrue(generateSequence(resolved.exceptionOrNull()) { it.cause }.any { it.message.orEmpty().contains("APPLICATION_EMAIL_FROM") }) { resolved.toString() }
    }

    @Test
    fun `should name the missing code secret variable`() = runUnitTest {
        given()
        val fixture = SutFixture(CONFIGURED - "APPLICATION_EMAIL_CODE_SECRET")

        whenn()
        val resolved = runCatching { fixture.scope.get<EmailCodeIssuer>() }

        then()
        assertTrue(generateSequence(resolved.exceptionOrNull()) { it.cause }.any { it.message.orEmpty().contains("APPLICATION_EMAIL_CODE_SECRET") }) { resolved.toString() }
    }

    @Test
    fun `should refuse a code secret too short to key the hash`() = runUnitTest {
        given()
        val fixture = SutFixture(CONFIGURED + ("APPLICATION_EMAIL_CODE_SECRET" to "short"))

        whenn()
        val resolved = runCatching { fixture.scope.get<EmailCodeRedeemer>() }

        then()
        assertTrue(generateSequence(resolved.exceptionOrNull()) { it.cause }.any { it.message.orEmpty().contains("APPLICATION_EMAIL_CODE_SECRET") }) { resolved.toString() }
    }

    private companion object {
        val SCOPE = named("EmailModuleTestScope")
        val CONFIGURED = mapOf(
            "APPLICATION_POSTMARK_SERVER_TOKEN" to "POSTMARK_API_TEST",
            "APPLICATION_EMAIL_FROM" to "no-reply@example.com",
            "APPLICATION_EMAIL_CODE_SECRET" to "0123456789abcdef0123456789abcdef-test-secret",
        )
    }
}
