package library.encryption.impl

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MeteredMissingKeysTest {

    private class SutFixture {
        val meterRegistry = SimpleMeterRegistry()
        val sut = MeteredMissingKeys(meterRegistry)

        fun count(source: String): Double {
            val counter = meterRegistry.find(MeteredMissingKeys.METRIC).tag(MeteredMissingKeys.SOURCE_TAG, source)
            return counter.counter()?.count() ?: 0.0
        }
    }

    @Test
    fun `should count every missing key under the source that could not read its data`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut.report("profile")
        fixture.sut.report("profile")
        fixture.sut.report("user.updated")

        then()
        assertEquals(2.0, fixture.count("profile"))
        assertEquals(1.0, fixture.count("user.updated"))
    }
}
