package com.bookk.server

import com.bookk.appointments.domain.impl.di.AppointmentsScope
import com.bookk.appointments.microservice.registerAppointmentsJobs
import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.auth.microservice.registerAuthJobs
import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.registerBusinessJobs
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.startScopedApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.scheduler.SchedulerConfiguration
import library.scheduler.test.registeredJobs
import library.scheduler.test.runJob
import library.signing.RotateSigningKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MonolithJobsTest {

    private class SutFixture {
        val authRotateSigningKeys = mockk<RotateSigningKeys>()
        val businessRotateSigningKeys = mockk<RotateSigningKeys>()
        val scheduler = SchedulerConfiguration()

        suspend fun start(builder: ApplicationTestBuilder): Application = builder.startScopedApplication(
            AuthScope to { scoped { authRotateSigningKeys } },
            BusinessScope to { scoped { businessRotateSigningKeys } },
            AppointmentsScope to {}
        )
    }

    private fun Application.jobsRegisteredBy(register: Application.(SchedulerConfiguration) -> Unit) =
        SchedulerConfiguration().also { register(it) }.registeredJobs()

    @Test
    fun `should register every service's jobs in one scheduler without name clashes`() = routeTest {
        given()
        val fixture = SutFixture()
        val application = fixture.start(this)
        val expectedJobs = application.jobsRegisteredBy { registerAuthJobs(it) } +
            application.jobsRegisteredBy { registerBusinessJobs(it) } +
            application.jobsRegisteredBy { registerAppointmentsJobs(it) }

        whenn()
        application.registerMonolithJobs(fixture.scheduler)

        then()
        assertEquals(expectedJobs, fixture.scheduler.registeredJobs())
    }

    @Test
    fun `should rotate auth signing keys through the auth scope only`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.authRotateSigningKeys.invoke(any()) } returns Result.success(Unit)
        fixture.start(this).registerMonolithJobs(fixture.scheduler)

        whenn()
        fixture.scheduler.runJob("rotateSigningKeys")

        then()
        coVerify(exactly = 1) { fixture.authRotateSigningKeys.invoke(any()) }
        coVerify(exactly = 0) { fixture.businessRotateSigningKeys.invoke(any()) }
    }

    @Test
    fun `should rotate business signing keys through the business scope only`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.businessRotateSigningKeys.invoke(any()) } returns Result.success(Unit)
        fixture.start(this).registerMonolithJobs(fixture.scheduler)

        whenn()
        fixture.scheduler.runJob("business-rotateSigningKeys")

        then()
        coVerify(exactly = 1) { fixture.businessRotateSigningKeys.invoke(any()) }
        coVerify(exactly = 0) { fixture.authRotateSigningKeys.invoke(any()) }
    }
}
