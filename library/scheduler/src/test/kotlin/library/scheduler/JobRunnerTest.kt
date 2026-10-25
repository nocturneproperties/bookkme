package library.scheduler

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class JobRunnerTest {

    @Test
    fun `should run job repeatedly at fixed interval`() = runUnitTest {
        given()
        var executions = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val job = ScheduledJob(
            name = "test-job",
            interval = 10.seconds,
            initialDelay = Duration.ZERO,
            action = { executions++ }
        )
        val runner = JobRunner(listOf(job), dispatcher, mockk<Logger>(relaxed = true))

        whenn()
        runner.start()
        runCurrent()
        advanceTimeBy(10.seconds)
        runCurrent()
        advanceTimeBy(10.seconds)
        runCurrent()

        then()
        assertEquals(3, executions)
        runner.stop()
    }

    @Test
    fun `should wait for initial delay before first execution`() = runUnitTest {
        given()
        var executions = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val job = ScheduledJob(
            name = "test-job",
            interval = 10.seconds,
            initialDelay = 5.seconds,
            action = { executions++ }
        )
        val runner = JobRunner(listOf(job), dispatcher, mockk<Logger>(relaxed = true))

        whenn()
        runner.start()
        runCurrent()

        then()
        assertEquals(0, executions)
        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(1, executions)
        runner.stop()
    }

    @Test
    fun `should run multiple jobs independently`() = runUnitTest {
        given()
        var fastExecutions = 0
        var slowExecutions = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fastJob = ScheduledJob(
            name = "fast-job",
            interval = 5.seconds,
            initialDelay = Duration.ZERO,
            action = { fastExecutions++ }
        )
        val slowJob = ScheduledJob(
            name = "slow-job",
            interval = 20.seconds,
            initialDelay = Duration.ZERO,
            action = { slowExecutions++ }
        )
        val runner = JobRunner(listOf(fastJob, slowJob), dispatcher, mockk<Logger>(relaxed = true))

        whenn()
        runner.start()
        runCurrent()
        advanceTimeBy(20.seconds)
        runCurrent()

        then()
        assertEquals(5, fastExecutions)
        assertEquals(2, slowExecutions)
        runner.stop()
    }

    @Test
    fun `should keep running after job action throws`() = runUnitTest {
        given()
        var executions = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val job = ScheduledJob(
            name = "failing-job",
            interval = 10.seconds,
            initialDelay = Duration.ZERO,
            action = {
                executions++
                if (executions == 1) throw RuntimeException("boom")
            }
        )
        val runner = JobRunner(listOf(job), dispatcher, mockk<Logger>(relaxed = true))

        whenn()
        runner.start()
        runCurrent()
        advanceTimeBy(10.seconds)
        runCurrent()

        then()
        assertEquals(2, executions)
        runner.stop()
    }

    @Test
    fun `should stop running jobs when stopped`() = runUnitTest {
        given()
        var executions = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val job = ScheduledJob(
            name = "test-job",
            interval = 10.seconds,
            initialDelay = Duration.ZERO,
            action = { executions++ }
        )
        val runner = JobRunner(listOf(job), dispatcher, mockk<Logger>(relaxed = true))
        runner.start()
        runCurrent()

        whenn()
        runner.stop()
        advanceTimeBy(50.seconds)
        runCurrent()

        then()
        assertEquals(1, executions)
    }
}
