package com.bookk.business.microservice

import com.bookk.business.domain.api.business.operation.DeleteDayOffsInThePast
import com.bookk.business.domain.api.client.operation.DeleteInactiveClients
import com.bookk.business.domain.api.employee.operation.DeleteProcessedEmployeeInvitations
import com.bookk.business.domain.api.employee.operation.ExpireEmployeeInvitations
import com.bookk.business.domain.api.erasure.operation.DeleteExpiredErasureRecords
import com.bookk.business.domain.api.erasure.operation.ReapplyClientErasures
import com.bookk.business.domain.impl.di.BusinessScope
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

internal class BusinessJobsTest {

    private class SutFixture {
        val rotateSigningKeys = mockk<RotateSigningKeys>()
        val deleteDayOffsInThePast = mockk<DeleteDayOffsInThePast>()
        val expireEmployeeInvitations = mockk<ExpireEmployeeInvitations>()
        val deleteProcessedEmployeeInvitations = mockk<DeleteProcessedEmployeeInvitations>()
        val deleteInactiveClients = mockk<DeleteInactiveClients>()
        val reapplyClientErasures = mockk<ReapplyClientErasures>()
        val deleteExpiredErasureRecords = mockk<DeleteExpiredErasureRecords>()
        val scheduler = SchedulerConfiguration()

        suspend fun registerJobs(builder: ApplicationTestBuilder) {
            val application = builder.startScopedApplication(BusinessScope) {
                scoped { rotateSigningKeys }
                scoped { deleteDayOffsInThePast }
                scoped { expireEmployeeInvitations }
                scoped { deleteProcessedEmployeeInvitations }
                scoped { deleteInactiveClients }
                scoped { reapplyClientErasures }
                scoped { deleteExpiredErasureRecords }
            }
            application.registerBusinessJobs(scheduler)
        }
    }

    @Test
    fun `should register every business job with its interval`() = routeTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.registerJobs(this)

        then()
        assertEquals(
            mapOf(
                "business-rotateSigningKeys" to 7.days,
                "business-deleteDayOffsInThePast" to 1.days,
                "business-expireEmployeeInvitations" to 1.days,
                "business-deleteProcessedEmployeeInvitations" to 1.days,
                "business-deleteInactiveClients" to 1.days,
                "business-reapplyErasures" to 1.minutes,
                "business-deleteExpiredErasureRecords" to 1.days,
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
        fixture.scheduler.runJob("business-rotateSigningKeys")

        then()
        coVerify(exactly = 1) { fixture.rotateSigningKeys.invoke(retireInterval = 7.days) }
    }

    @Test
    fun `should surface a failed signing key rotation`() = routeTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("rotation failed")
        coEvery { fixture.rotateSigningKeys.invoke(any()) } returns Result.failure(error)
        fixture.registerJobs(this)

        whenn()
        val result = runCatching { fixture.scheduler.runJob("business-rotateSigningKeys") }

        then()
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun `should delete day offs in the past`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.deleteDayOffsInThePast.invoke() } returns Result.success(Unit)
        fixture.registerJobs(this)

        whenn()
        fixture.scheduler.runJob("business-deleteDayOffsInThePast")

        then()
        coVerify(exactly = 1) { fixture.deleteDayOffsInThePast.invoke() }
    }

    @Test
    fun `should surface a failed day off cleanup`() = routeTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("cleanup failed")
        coEvery { fixture.deleteDayOffsInThePast.invoke() } returns Result.failure(error)
        fixture.registerJobs(this)

        whenn()
        val result = runCatching { fixture.scheduler.runJob("business-deleteDayOffsInThePast") }

        then()
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun `should expire employee invitations`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.expireEmployeeInvitations.invoke() } returns Result.success(Unit)
        fixture.registerJobs(this)

        whenn()
        fixture.scheduler.runJob("business-expireEmployeeInvitations")

        then()
        coVerify(exactly = 1) { fixture.expireEmployeeInvitations.invoke() }
    }

    @Test
    fun `should surface a failed invitation expiry`() = routeTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("expiry failed")
        coEvery { fixture.expireEmployeeInvitations.invoke() } returns Result.failure(error)
        fixture.registerJobs(this)

        whenn()
        val result = runCatching { fixture.scheduler.runJob("business-expireEmployeeInvitations") }

        then()
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun `should delete processed employee invitations`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.deleteProcessedEmployeeInvitations.invoke() } returns Result.success(Unit)
        fixture.registerJobs(this)

        whenn()
        fixture.scheduler.runJob("business-deleteProcessedEmployeeInvitations")

        then()
        coVerify(exactly = 1) { fixture.deleteProcessedEmployeeInvitations.invoke() }
    }

    @Test
    fun `should surface a failed processed invitation cleanup`() = routeTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("cleanup failed")
        coEvery { fixture.deleteProcessedEmployeeInvitations.invoke() } returns Result.failure(error)
        fixture.registerJobs(this)

        whenn()
        val result = runCatching { fixture.scheduler.runJob("business-deleteProcessedEmployeeInvitations") }

        then()
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun `should delete clients inactive for longer than their business's retention period`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.deleteInactiveClients.invoke() } returns Result.success(Unit)
        fixture.registerJobs(this)

        whenn()
        fixture.scheduler.runJob("business-deleteInactiveClients")

        then()
        coVerify(exactly = 1) { fixture.deleteInactiveClients.invoke() }
    }

    @Test
    fun `should surface a failed inactive client deletion`() = routeTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("inactive client deletion failed")
        coEvery { fixture.deleteInactiveClients.invoke() } returns Result.failure(error)
        fixture.registerJobs(this)

        whenn()
        val result = runCatching { fixture.scheduler.runJob("business-deleteInactiveClients") }

        then()
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun `should erase again the clients a restore brought back`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.reapplyClientErasures.invoke() } returns Result.success(Unit)
        fixture.registerJobs(this)

        whenn()
        fixture.scheduler.runJob("business-reapplyErasures")

        then()
        coVerify(exactly = 1) { fixture.reapplyClientErasures.invoke() }
    }

    @Test
    fun `should surface a failed erasure re-run`() = routeTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("erasure re-run failed")
        coEvery { fixture.reapplyClientErasures.invoke() } returns Result.failure(error)
        fixture.registerJobs(this)

        whenn()
        val result = runCatching { fixture.scheduler.runJob("business-reapplyErasures") }

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
        fixture.scheduler.runJob("business-deleteExpiredErasureRecords")

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
        val result = runCatching { fixture.scheduler.runJob("business-deleteExpiredErasureRecords") }

        then()
        assertSame(error, result.exceptionOrNull())
    }
}
