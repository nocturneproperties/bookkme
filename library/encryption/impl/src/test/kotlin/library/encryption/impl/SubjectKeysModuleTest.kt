package library.encryption.impl

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import library.encryption.BlindIndex
import library.encryption.MissingKeys
import library.encryption.SubjectKeys
import library.encryption.impl.di.subjectKeys
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal class SubjectKeysModuleTest {

    private class SutFixture(environment: Map<String, String>) {
        private val keys = module {
            single<MeterRegistry> { SimpleMeterRegistry() }
            scope(SCOPE) { subjectKeys("test_keys", environment::get) }
        }
        private val koin = koinApplication { modules(keys) }.koin
        val scope = koin.createScope("keys", SCOPE)
    }

    private fun Result<*>.mentions(text: String): Boolean {
        val causes = generateSequence(exceptionOrNull()) { it.cause }
        return causes.any { it.message.orEmpty().contains(text) }
    }

    @Test
    fun `should provide the subject keys, the blind index and the missing key report in the service's scope`() =
        runUnitTest {
            given()
            val fixture = SutFixture(CONFIGURED)

            whenn()
            val resolved = runCatching {
                listOf(
                    fixture.scope.get<SubjectKeys>(),
                    fixture.scope.get<BlindIndex>(),
                    fixture.scope.get<MissingKeys>(),
                )
            }

            then()
            assertTrue(resolved.isSuccess) { resolved.toString() }
        }

    @Test
    fun `should name the missing master key variable`() = runUnitTest {
        given()
        val fixture = SutFixture(CONFIGURED - MASTER_KEY)

        whenn()
        val resolved = runCatching { fixture.scope.get<SubjectKeys>() }

        then()
        assertTrue(resolved.mentions(MASTER_KEY)) { resolved.toString() }
    }

    @Test
    fun `should refuse a master key that is not 256 bits`() = runUnitTest {
        given()
        val fixture = SutFixture(CONFIGURED + (MASTER_KEY to randomBase64Key(bytes = 16)))

        whenn()
        val resolved = runCatching { fixture.scope.get<SubjectKeys>() }

        then()
        assertTrue(resolved.mentions(MASTER_KEY)) { resolved.toString() }
    }

    @Test
    fun `should name the missing blind index key variable`() = runUnitTest {
        given()
        val fixture = SutFixture(CONFIGURED - BLIND_INDEX_KEY)

        whenn()
        val resolved = runCatching { fixture.scope.get<BlindIndex>() }

        then()
        assertTrue(resolved.mentions(BLIND_INDEX_KEY)) { resolved.toString() }
    }

    @Test
    fun `should refuse a blind index key that is not valid Base64`() = runUnitTest {
        given()
        val fixture = SutFixture(CONFIGURED + (BLIND_INDEX_KEY to "not base64 !"))

        whenn()
        val resolved = runCatching { fixture.scope.get<BlindIndex>() }

        then()
        assertTrue(resolved.mentions(BLIND_INDEX_KEY)) { resolved.toString() }
    }

    private companion object {
        val SCOPE = named("SubjectKeysModuleTestScope")
        const val MASTER_KEY = "APPLICATION_SUBJECT_MASTER_KEY"
        const val BLIND_INDEX_KEY = "APPLICATION_BLIND_INDEX_KEY"
        val CONFIGURED = mapOf(MASTER_KEY to randomBase64Key(), BLIND_INDEX_KEY to randomBase64Key())
    }
}
