package com.bookk.auth.domain.impl.operation.demo

import com.bookk.auth.domain.api.authentication.entity.DemoAccount
import com.bookk.auth.domain.api.demo.entity.DemoAccountPolicy
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.impl.operation.AccountEraser
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal class DeleteExpiredDemoAccountsImplTest {

    private class SutFixture {
        val accountDataSource = mockk<AccountDataSource>()
        val accountEraser = mockk<AccountEraser>(relaxUnitFun = true)
        val transactionManager = mockk<TransactionManager>()
        val meterRegistry = SimpleMeterRegistry()
        val sut = DeleteExpiredDemoAccountsImpl(accountDataSource, accountEraser, transactionManager, meterRegistry)
        val cutoff = slot<Instant>()

        init {
            transactionManager.mockTransaction()
        }

        fun listed(vararg accounts: DemoAccount) {
            coEvery { accountDataSource.getDemoAccountsExpiredBefore(capture(cutoff)) } returns accounts.toList()
            accounts.forEach { coEvery { accountDataSource.getDemoAccountForUpdate(it.authId) } returns it }
        }

        fun erasedDemos(): Double {
            return meterRegistry.find(DeleteExpiredDemoAccountsImpl.DEMO_ERASED_METRIC).counter()?.count() ?: 0.0
        }
    }

    private fun endedDaysAgo(days: Int, deletionHold: Boolean = false): DemoAccount {
        return DemoAccount.stub(expiresAt = Clock.System.now() - days.days, deletionHold = deletionHold)
    }

    @Test
    fun `should list only demos that ended before the upgrade grace period`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.listed()
        val earliest = Clock.System.now() - DemoAccountPolicy.UPGRADE_GRACE_PERIOD

        whenn()
        fixture.sut.invoke()

        then()
        assertTrue(fixture.cutoff.captured in earliest..earliest + 1.minutes) { fixture.cutoff.captured.toString() }
    }

    @Test
    fun `should erase every listed demo through the account eraser after locking it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = endedDaysAgo(8)
        val second = endedDaysAgo(20)
        fixture.listed(first, second)

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerifyOrder {
            fixture.accountDataSource.getDemoAccountForUpdate(first.authId)
            fixture.accountEraser.erase(first.authId, first.userId)
        }
        coVerify(exactly = 1) { fixture.accountEraser.erase(second.authId, second.userId) }
        assertEquals(2.0, fixture.erasedDemos())
    }

    @Test
    fun `should keep a demo whose deletion is on hold`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val held = endedDaysAgo(8, deletionHold = true)
        fixture.listed(held)

        whenn()
        fixture.sut.invoke()

        then()
        coVerify(exactly = 0) { fixture.accountEraser.erase(any(), any()) }
        assertEquals(0.0, fixture.erasedDemos())
    }

    @Test
    fun `should keep a demo put on hold after it was listed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val listed = endedDaysAgo(8)
        fixture.listed(listed)
        coEvery { fixture.accountDataSource.getDemoAccountForUpdate(listed.authId) } returns
            listed.copy(deletionHold = true)

        whenn()
        fixture.sut.invoke()

        then()
        coVerify(exactly = 0) { fixture.accountEraser.erase(any(), any()) }
    }

    @Test
    fun `should keep a demo upgraded after it was listed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val listed = endedDaysAgo(8)
        fixture.listed(listed)
        coEvery { fixture.accountDataSource.getDemoAccountForUpdate(listed.authId) } returns null

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 0) { fixture.accountEraser.erase(any(), any()) }
    }

    @Test
    fun `should erase the other demos and report the failure when one erasure fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val failing = endedDaysAgo(8)
        val other = endedDaysAgo(9)
        val error = RuntimeException("erasure failed")
        fixture.listed(failing, other)
        coEvery { fixture.accountEraser.erase(failing.authId, failing.userId) } throws error

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertSame(error, result.exceptionOrNull())
        coVerify(exactly = 1) { fixture.accountEraser.erase(other.authId, other.userId) }
    }
}
