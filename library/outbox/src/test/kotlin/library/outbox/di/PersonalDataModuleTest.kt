package library.outbox.di

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.mockk
import library.encryption.MissingKeys
import library.encryption.SubjectKeys
import library.outbox.personal.NoPersonalDataCipher
import library.outbox.personal.PersonalDataCipher
import library.outbox.personal.SubjectKeysPersonalDataCipher
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.ScopeDSL
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal class PersonalDataModuleTest {

    private class SutFixture(personalData: ScopeDSL.() -> Unit) {
        val qualifier = named("personal-data-test")
        val infrastructure = module {
            scope(qualifier) {
                scoped<SubjectKeys> { mockk() }
                scoped<MissingKeys> { mockk() }
                personalData()
            }
        }
        val scope = koinApplication { modules(infrastructure) }
            .koin
            .createScope("personal-data-test-scope", qualifier)
    }

    @Test
    fun `should encrypt personal data with the service's subject keys`() = runUnitTest {
        given()
        val fixture = SutFixture { personalDataCipher() }

        whenn()
        val cipher = fixture.scope.get<PersonalDataCipher>()

        then()
        assertTrue(cipher is SubjectKeysPersonalDataCipher)
    }

    @Test
    fun `should refuse personal data in a service without subject keys`() = runUnitTest {
        given()
        val fixture = SutFixture { noPersonalDataCipher() }

        whenn()
        val cipher = fixture.scope.get<PersonalDataCipher>()

        then()
        assertSame(NoPersonalDataCipher, cipher)
    }
}
