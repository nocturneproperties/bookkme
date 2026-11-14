package com.bookk.notifications.microservice

import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.startScopedApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.api.EncryptPlaintextTargets
import com.bookk.notifications.domain.impl.di.NotificationsScope
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
import kotlin.time.Duration.Companion.minutes

internal class NotificationsJobsTest {

    private class SutFixture {
        val encryptPlaintextTargets = mockk<EncryptPlaintextTargets>()
        val scheduler = SchedulerConfiguration()

        suspend fun registerJobs(builder: ApplicationTestBuilder) {
            val application = builder.startScopedApplication(NotificationsScope) {
                scoped { encryptPlaintextTargets }
            }
            application.registerNotificationsJobs(scheduler)
        }
    }

    @Test
    fun `should register every notifications job with its interval`() = routeTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.registerJobs(this)

        then()
        assertEquals(
            mapOf("notifications-encryptPlaintextTargets" to 10.minutes),
            fixture.scheduler.registeredJobs(),
        )
    }

    @Test
    fun `should encrypt the targets still stored as plaintext`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.encryptPlaintextTargets.invoke() } returns Result.success(Unit)
        fixture.registerJobs(this)

        whenn()
        fixture.scheduler.runJob("notifications-encryptPlaintextTargets")

        then()
        coVerify(exactly = 1) { fixture.encryptPlaintextTargets.invoke() }
    }

    @Test
    fun `should surface a failed target encryption`() = routeTest {
        given()
        val fixture = SutFixture()
        val error = IllegalStateException("The stored target has a shredded key")
        coEvery { fixture.encryptPlaintextTargets.invoke() } returns Result.failure(error)
        fixture.registerJobs(this)

        whenn()
        val result = runCatching { fixture.scheduler.runJob("notifications-encryptPlaintextTargets") }

        then()
        assertSame(error, result.exceptionOrNull())
    }
}
