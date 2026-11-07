package com.bookk.core.coroutines

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal class TicksTest {

    private class SutFixture {
        val tickedAt = ConcurrentLinkedQueue<Long>()
    }

    @Test
    fun `should tick as soon as it is collected`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        ticks(INTERVAL).onEach { fixture.tickedAt += currentTime }.launchIn(backgroundScope)
        runCurrent()

        then()
        assertEquals(listOf(0L), fixture.tickedAt.toList())
    }

    @Test
    fun `should tick again once every interval`() = runUnitTest {
        given()
        val fixture = SutFixture()
        ticks(INTERVAL).onEach { fixture.tickedAt += currentTime }.launchIn(backgroundScope)
        runCurrent()

        whenn()
        advanceTimeBy(INTERVAL - 1.milliseconds)
        runCurrent()
        val beforeInterval = fixture.tickedAt.size
        advanceTimeBy(1.milliseconds)
        runCurrent()
        advanceTimeBy(INTERVAL)
        runCurrent()

        then()
        assertEquals(1, beforeInterval)
        assertEquals(listOf(0L, INTERVAL.inWholeMilliseconds, 2 * INTERVAL.inWholeMilliseconds), fixture.tickedAt.toList())
    }

    @Test
    fun `should wait a full interval after a slow collector finishes a tick`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val slowTick = 2.minutes

        whenn()
        ticks(INTERVAL).onEach {
            fixture.tickedAt += currentTime
            delay(slowTick)
        }.launchIn(backgroundScope)
        runCurrent()
        advanceTimeBy(slowTick + INTERVAL)
        runCurrent()

        then()
        assertEquals(listOf(0L, (slowTick + INTERVAL).inWholeMilliseconds), fixture.tickedAt.toList())
    }

    private companion object {
        val INTERVAL = 1.minutes
    }
}
