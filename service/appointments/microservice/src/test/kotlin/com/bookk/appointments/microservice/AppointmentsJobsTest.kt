package com.bookk.appointments.microservice

import com.bookk.appointments.domain.api.operation.DeleteOutdatedRequests
import com.bookk.appointments.domain.api.operation.MarkAppointmentsCompleted
import com.bookk.appointments.domain.impl.di.AppointmentsScope
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.startScopedApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.scheduler.SchedulerConfiguration
import library.scheduler.test.registeredJobs
import library.scheduler.test.runJob
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

internal class AppointmentsJobsTest {

    private class SutFixture {
        val markAppointmentsCompleted = mockk<MarkAppointmentsCompleted>()
        val deleteOutdatedRequests = mockk<DeleteOutdatedRequests>()
        val scheduler = SchedulerConfiguration()

        suspend fun registerJobs(builder: ApplicationTestBuilder) {
            val application = builder.startScopedApplication(AppointmentsScope) {
                scoped { markAppointmentsCompleted }
                scoped { deleteOutdatedRequests }
            }
            application.registerAppointmentsJobs(scheduler)
        }
    }

    @Test
    fun `should register every appointments job with its interval`() = routeTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.registerJobs(this)

        then()
        assertEquals(
            mapOf(
                "markAppointmentsAsCompleted" to 5.minutes,
                "deleteOutdatedRequests" to 1.hours
            ),
            fixture.scheduler.registeredJobs()
        )
    }

    @Test
    fun `should mark past appointments as completed`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.markAppointmentsCompleted.invoke() } returns Result.success(Unit)
        fixture.registerJobs(this)

        whenn()
        fixture.scheduler.runJob("markAppointmentsAsCompleted")

        then()
        coVerify(exactly = 1) { fixture.markAppointmentsCompleted.invoke() }
    }

    @Test
    fun `should surface a failed appointment completion`() = routeTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("appointment completion failed")
        coEvery { fixture.markAppointmentsCompleted.invoke() } returns Result.failure(error)
        fixture.registerJobs(this)

        whenn()
        val result = runCatching { fixture.scheduler.runJob("markAppointmentsAsCompleted") }

        then()
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun `should delete outdated appointment requests`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.deleteOutdatedRequests.invoke() } returns Result.success(Unit)
        fixture.registerJobs(this)

        whenn()
        fixture.scheduler.runJob("deleteOutdatedRequests")

        then()
        coVerify(exactly = 1) { fixture.deleteOutdatedRequests.invoke() }
    }

    @Test
    fun `should surface a failed outdated request cleanup`() = routeTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("outdated request cleanup failed")
        coEvery { fixture.deleteOutdatedRequests.invoke() } returns Result.failure(error)
        fixture.registerJobs(this)

        whenn()
        val result = runCatching { fixture.scheduler.runJob("deleteOutdatedRequests") }

        then()
        assertSame(error, result.exceptionOrNull())
    }
}
