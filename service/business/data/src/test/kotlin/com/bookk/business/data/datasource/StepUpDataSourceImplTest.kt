package com.bookk.business.data.datasource

import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

internal class StepUpDataSourceImplTest {

    private class SutFixture {
        val timeSource = TestTimeSource()
        val sut = StepUpDataSourceImpl(InMemoryCacheClient(timeSource = timeSource))
    }

    @Test
    fun `should reserve a step-up token that was never used`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val reserved = fixture.sut.reserve("token-id", 2.minutes)

        then()
        assertTrue(reserved)
    }

    @Test
    fun `should not reserve a step-up token twice`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.reserve("token-id", 2.minutes)

        whenn()
        val reservedAgain = fixture.sut.reserve("token-id", 2.minutes)

        then()
        assertFalse(reservedAgain)
    }

    @Test
    fun `should reserve a released step-up token again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.reserve("token-id", 2.minutes)
        fixture.sut.release("token-id")

        whenn()
        val reservedAgain = fixture.sut.reserve("token-id", 2.minutes)

        then()
        assertTrue(reservedAgain)
    }

    @Test
    fun `should forget a reservation once the token has expired`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.reserve("token-id", 2.minutes)
        fixture.timeSource += 3.minutes

        whenn()
        val reservedAgain = fixture.sut.reserve("token-id", 2.minutes)

        then()
        assertTrue(reservedAgain)
    }
}
