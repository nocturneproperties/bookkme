package com.bookk.auth.microservice

import com.bookk.auth.domain.api.authentication.operation.HandleDormantAccounts
import com.bookk.auth.domain.api.device.operation.DeleteInactiveDevices
import com.bookk.auth.domain.api.erasure.operation.DeleteExpiredErasureRecords
import com.bookk.auth.domain.api.erasure.operation.ReapplyAccountErasures
import com.bookk.auth.domain.impl.di.AuthScope
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
import library.signing.RotateSigningKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

internal class AuthJobsTest {

    private class SutFixture {
        val rotateSigningKeys = mockk<RotateSigningKeys>()
        val deleteInactiveDevices = mockk<DeleteInactiveDevices>()
        val handleDormantAccounts = mockk<HandleDormantAccounts>()
        val reapplyAccountErasures = mockk<ReapplyAccountErasures>()
        val deleteExpiredErasureRecords = mockk<DeleteExpiredErasureRecords>()
        val scheduler = SchedulerConfiguration()

        suspend fun registerJobs(builder: ApplicationTestBuilder) {
            val application = builder.startScopedApplication(AuthScope) {
                scoped { rotateSigningKeys }
                scoped { deleteInactiveDevices }
                scoped { handleDormantAccounts }
                scoped { reapplyAccountErasures }
                scoped { deleteExpiredErasureRecords }
            }
            application.registerAuthJobs(scheduler)
        }
    }

    @Test
    fun `should register every auth job with its interval`() = routeTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.registerJobs(this)

        then()
        assertEquals(
            mapOf(
                "auth-rotateSigningKeys" to 1.days,
                "auth-deleteInactiveDevices" to 1.days,
                "auth-handleDormantAccounts" to 1.days,
                "auth-reapplyErasures" to 1.minutes,
                "auth-deleteExpiredErasureRecords" to 1.days,
            ),
            fixture.scheduler.registeredJobs(),
        )
    }

    @Test
    fun `should rotate signing keys with a 7 day retire interval`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.rotateSigningKeys.invoke(any()) } returns Result.success(Unit)
        fixture.registerJobs(this)

        whenn()
        fixture.scheduler.runJob("auth-rotateSigningKeys")

        then()
        coVerify(exactly = 1) { fixture.rotateSigningKeys.invoke(retireInterval = 7.days) }
    }

    @Test
    fun `should surface a failed signing key rotation`() = routeTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("signing key rotation failed")
        coEvery { fixture.rotateSigningKeys.invoke(any()) } returns Result.failure(error)
        fixture.registerJobs(this)

        whenn()
        val result = runCatching { fixture.scheduler.runJob("auth-rotateSigningKeys") }

        then()
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun `should delete inactive devices`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.deleteInactiveDevices.invoke() } returns Result.success(Unit)
        fixture.registerJobs(this)

        whenn()
        fixture.scheduler.runJob("auth-deleteInactiveDevices")

        then()
        coVerify(exactly = 1) { fixture.deleteInactiveDevices.invoke() }
    }

    @Test
    fun `should surface a failed inactive device cleanup`() = routeTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("inactive device cleanup failed")
        coEvery { fixture.deleteInactiveDevices.invoke() } returns Result.failure(error)
        fixture.registerJobs(this)

        whenn()
        val result = runCatching { fixture.scheduler.runJob("auth-deleteInactiveDevices") }

        then()
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun `should warn and delete accounts inactive for three years`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.handleDormantAccounts.invoke() } returns Result.success(Unit)
        fixture.registerJobs(this)

        whenn()
        fixture.scheduler.runJob("auth-handleDormantAccounts")

        then()
        coVerify(exactly = 1) { fixture.handleDormantAccounts.invoke() }
    }

    @Test
    fun `should surface a failed dormant account run`() = routeTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("dormant accounts failed")
        coEvery { fixture.handleDormantAccounts.invoke() } returns Result.failure(error)
        fixture.registerJobs(this)

        whenn()
        val result = runCatching { fixture.scheduler.runJob("auth-handleDormantAccounts") }

        then()
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun `should erase again the accounts a restore brought back`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.reapplyAccountErasures.invoke() } returns Result.success(Unit)
        fixture.registerJobs(this)

        whenn()
        fixture.scheduler.runJob("auth-reapplyErasures")

        then()
        coVerify(exactly = 1) { fixture.reapplyAccountErasures.invoke() }
    }

    @Test
    fun `should surface a failed erasure re-run`() = routeTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("erasure re-run failed")
        coEvery { fixture.reapplyAccountErasures.invoke() } returns Result.failure(error)
        fixture.registerJobs(this)

        whenn()
        val result = runCatching { fixture.scheduler.runJob("auth-reapplyErasures") }

        then()
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun `should delete expired erasure records`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.deleteExpiredErasureRecords.invoke() } returns Result.success(Unit)
        fixture.registerJobs(this)

        whenn()
        fixture.scheduler.runJob("auth-deleteExpiredErasureRecords")

        then()
        coVerify(exactly = 1) { fixture.deleteExpiredErasureRecords.invoke() }
    }

    @Test
    fun `should surface a failed erasure record cleanup`() = routeTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("erasure record cleanup failed")
        coEvery { fixture.deleteExpiredErasureRecords.invoke() } returns Result.failure(error)
        fixture.registerJobs(this)

        whenn()
        val result = runCatching { fixture.scheduler.runJob("auth-deleteExpiredErasureRecords") }

        then()
        assertSame(error, result.exceptionOrNull())
    }
}
