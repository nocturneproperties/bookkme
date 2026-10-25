package library.scheduler

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.scheduler.test.registeredJobs
import library.scheduler.test.runJob
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

internal class SchedulerTestExtensionsTest {

    @Test
    fun `should list registered jobs by name with their interval`() = runUnitTest {
        given()
        val configuration = SchedulerConfiguration()
        configuration.job("first", interval = 5.minutes) {}
        configuration.job("second", interval = 1.hours) {}

        whenn()
        val jobs = configuration.registeredJobs()

        then()
        assertEquals(mapOf("first" to 5.minutes, "second" to 1.hours), jobs)
    }

    @Test
    fun `should run only the named job once`() = runUnitTest {
        given()
        val configuration = SchedulerConfiguration()
        var firstRuns = 0
        var secondRuns = 0
        configuration.job("first", interval = 5.minutes) { firstRuns++ }
        configuration.job("second", interval = 5.minutes) { secondRuns++ }

        whenn()
        configuration.runJob("first")

        then()
        assertEquals(1, firstRuns)
        assertEquals(0, secondRuns)
    }

    @Test
    fun `should fail when running a job that is not registered`() = runUnitTest {
        given()
        val configuration = SchedulerConfiguration()

        whenn()
        val result = runCatching { configuration.runJob("missing") }

        then()
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
