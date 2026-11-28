package library.scheduler

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
            logSuccessfulRuns = true,
            action = { executions++ },
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
            logSuccessfulRuns = true,
            action = { executions++ },
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
            logSuccessfulRuns = true,
            action = { fastExecutions++ },
        )
        val slowJob = ScheduledJob(
            name = "slow-job",
            interval = 20.seconds,
            initialDelay = Duration.ZERO,
            logSuccessfulRuns = true,
            action = { slowExecutions++ },
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
            logSuccessfulRuns = true,
            action = {
                executions++
                if (executions == 1) throw RuntimeException("boom")
            },
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
            logSuccessfulRuns = true,
            action = { executions++ },
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

    @Test
    fun `should log one line per successful run`() = runUnitTest {
        given()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val logger = mockk<Logger>(relaxed = true)
        val job = ScheduledJob(
            name = "test-job",
            interval = 10.seconds,
            initialDelay = Duration.ZERO,
            logSuccessfulRuns = true,
            action = {},
        )
        val runner = JobRunner(listOf(job), dispatcher, logger)

        whenn()
        runner.start()
        runCurrent()
        runner.stop()

        then()
        val messages = mutableListOf<String>()
        verify(exactly = 1) { logger.debug(capture(messages)) }
        assertTrue(messages.single().startsWith("Scheduled job 'test-job' completed in "))
        verify(exactly = 0) { logger.info(any<String>()) }
        verify(exactly = 0) { logger.error(any<String>(), any<Throwable>()) }
    }

    @Test
    fun `should not log successful runs of a job registered without success logging`() = runUnitTest {
        given()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val logger = mockk<Logger>(relaxed = true)
        val job = ScheduledJob(
            name = "quiet-job",
            interval = 10.seconds,
            initialDelay = Duration.ZERO,
            logSuccessfulRuns = false,
            action = {},
        )
        val runner = JobRunner(listOf(job), dispatcher, logger)

        whenn()
        runner.start()
        runCurrent()
        advanceTimeBy(10.seconds)
        runCurrent()
        runner.stop()

        then()
        verify(exactly = 0) { logger.debug(any<String>()) }
        verify(exactly = 0) { logger.info(any<String>()) }
    }

    @Test
    fun `should log a failed run as one error even without success logging`() = runUnitTest {
        given()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val logger = mockk<Logger>(relaxed = true)
        val failure = RuntimeException("boom")
        val job = ScheduledJob(
            name = "quiet-job",
            interval = 10.seconds,
            initialDelay = Duration.ZERO,
            logSuccessfulRuns = false,
            action = { throw failure },
        )
        val runner = JobRunner(listOf(job), dispatcher, logger)

        whenn()
        runner.start()
        runCurrent()
        runner.stop()

        then()
        verify(exactly = 1) { logger.error("Scheduled job 'quiet-job' failed", failure) }
        verify(exactly = 0) { logger.debug(any<String>()) }
    }
}
