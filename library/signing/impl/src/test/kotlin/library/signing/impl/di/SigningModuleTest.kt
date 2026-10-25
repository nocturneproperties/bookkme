package library.signing.impl.di

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.mockk
import library.signing.GetActiveSigningKey
import library.signing.RotateSigningKeys
import library.signing.impl.GetActiveSigningKeyImpl
import library.signing.impl.RotateSigningKeysImpl
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal class SigningModuleTest {

    private class SutFixture {
        private val authQualifier = named("auth-test")
        private val businessQualifier = named("business-test")
        private val sharedModule = module {
            single<TransactionManager> { mockk() }
        }
        private val koin = koinApplication {
            modules(sharedModule, signingModule(authQualifier), signingModule(businessQualifier))
        }.koin

        fun serviceScopes(): List<Scope> = listOf(
            koin.createScope("auth-test-scope", authQualifier),
            koin.createScope("business-test-scope", businessQualifier)
        )
    }

    @Test
    fun `should resolve the key-generating signing operations in every service scope`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val operations = fixture.serviceScopes().map { scope ->
            scope.get<GetActiveSigningKey>() to scope.get<RotateSigningKeys>()
        }

        then()
        operations.forEach { (getActiveSigningKey, rotateSigningKeys) ->
            assertTrue(getActiveSigningKey is GetActiveSigningKeyImpl)
            assertTrue(rotateSigningKeys is RotateSigningKeysImpl)
        }
    }
}
