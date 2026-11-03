package com.bookk.user.microservice

import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.startScopedApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.impl.di.UserScope
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.export.BuildDataExports
import library.export.DeleteExpiredDataExports
import library.scheduler.SchedulerConfiguration
import library.scheduler.test.registeredJobs
import library.scheduler.test.runJob
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

internal class UserJobsTest {

    private class SutFixture {
        val buildDataExports = mockk<BuildDataExports>()
        val deleteExpiredDataExports = mockk<DeleteExpiredDataExports>()
        val scheduler = SchedulerConfiguration()

        suspend fun registerJobs(builder: ApplicationTestBuilder) {
            val application = builder.startScopedApplication(UserScope) {
                scoped { buildDataExports }
                scoped { deleteExpiredDataExports }
            }
            application.registerUserJobs(scheduler)
        }
    }

    @Test
    fun `should register every user job with its interval`() = routeTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.registerJobs(this)

        then()
        assertEquals(
            mapOf(
                "user-buildDataExports" to 10.seconds,
                "user-deleteExpiredDataExports" to 1.hours,
            ),
            fixture.scheduler.registeredJobs(),
        )
    }

    @Test
    fun `should build the queued data exports`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.buildDataExports.invoke() } returns Result.success(Unit)
        fixture.registerJobs(this)

        whenn()
        fixture.scheduler.runJob("user-buildDataExports")

        then()
        coVerify(exactly = 1) { fixture.buildDataExports.invoke() }
    }

    @Test
    fun `should surface a failed data export build`() = routeTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("queue unreadable")
        coEvery { fixture.buildDataExports.invoke() } returns Result.failure(error)
        fixture.registerJobs(this)

        whenn()
        val result = runCatching { fixture.scheduler.runJob("user-buildDataExports") }

        then()
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun `should delete expired data exports`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.deleteExpiredDataExports.invoke() } returns Result.success(Unit)
        fixture.registerJobs(this)

        whenn()
        fixture.scheduler.runJob("user-deleteExpiredDataExports")

        then()
        coVerify(exactly = 1) { fixture.deleteExpiredDataExports.invoke() }
    }

    @Test
    fun `should surface a failed expired data export cleanup`() = routeTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("cleanup failed")
        coEvery { fixture.deleteExpiredDataExports.invoke() } returns Result.failure(error)
        fixture.registerJobs(this)

        whenn()
        val result = runCatching { fixture.scheduler.runJob("user-deleteExpiredDataExports") }

        then()
        assertSame(error, result.exceptionOrNull())
    }
}
