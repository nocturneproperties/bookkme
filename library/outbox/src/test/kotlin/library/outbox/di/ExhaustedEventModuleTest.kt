package library.outbox.di

import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.outbox.datasource.SealingExhaustedEventDataSource
import library.outbox.personal.NoPersonalDataCipher
import library.outbox.personal.PersonalDataCipher
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal class ExhaustedEventModuleTest {

    private class SutFixture {
        val qualifier = named("exhausted-event-test")
        val serviceModule = module {
            scope(qualifier) {
                scoped<PersonalDataCipher> { NoPersonalDataCipher }
                exhaustedEventDataSource()
            }
        }
        val scope = koinApplication { modules(serviceModule) }
            .koin
            .createScope("exhausted-event-test-scope", qualifier)
    }

    @Test
    fun `should seal and open parked events with the service's cipher`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val dataSource = fixture.scope.get<ExhaustedEventDataSource>()

        then()
        assertTrue(dataSource is SealingExhaustedEventDataSource)
    }
}
