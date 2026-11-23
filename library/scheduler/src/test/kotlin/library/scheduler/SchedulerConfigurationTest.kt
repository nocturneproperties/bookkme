package library.scheduler

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class SchedulerConfigurationTest {

    @Test
    fun `should register a job`() = runUnitTest {
        given()
        val configuration = SchedulerConfiguration()

        whenn()
        configuration.job(name = "test-job", interval = 10.seconds) {}

        then()
        assertEquals(1, configuration.jobs.size)
        assertEquals("test-job", configuration.jobs.first().name)
        assertTrue(configuration.jobs.first().logSuccessfulRuns)
    }

    @Test
    fun `should register a job without success logging`() = runUnitTest {
        given()
        val configuration = SchedulerConfiguration()

        whenn()
        configuration.job(name = "quiet-job", interval = 10.seconds, logSuccessfulRuns = false) {}

        then()
        assertFalse(configuration.jobs.single().logSuccessfulRuns)
    }

    @Test
    fun `should reject jobs with non-positive interval`() = runUnitTest {
        given()
        val configuration = SchedulerConfiguration()

        whenn()
        val exception = assertThrows(IllegalArgumentException::class.java) {
            configuration.job(name = "test-job", interval = Duration.ZERO) {}
        }

        then()
        assertEquals("Job 'test-job' interval must be positive", exception.message)
    }

    @Test
    fun `should reject duplicate job names`() = runUnitTest {
        given()
        val configuration = SchedulerConfiguration()
        configuration.job(name = "test-job", interval = 10.seconds) {}

        whenn()
        val exception = assertThrows(IllegalArgumentException::class.java) {
            configuration.job(name = "test-job", interval = 20.seconds) {}
        }

        then()
        assertEquals("Job 'test-job' is already scheduled", exception.message)
    }
}
