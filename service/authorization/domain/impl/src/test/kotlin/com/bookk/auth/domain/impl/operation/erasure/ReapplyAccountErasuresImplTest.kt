package com.bookk.auth.domain.impl.operation.erasure

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.ErasedAccountDataSource
import com.bookk.auth.domain.impl.operation.AccountEraser
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AuthEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class ReapplyAccountErasuresImplTest {

    private class SutFixture {
        val erasedAccountDataSource = mockk<ErasedAccountDataSource>()
        val accountDataSource = mockk<AccountDataSource>()
        val accountEraser = mockk<AccountEraser>(relaxUnitFun = true)
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val transactionManager = mockk<TransactionManager>().apply { mockTransaction() }
        val sut =
            ReapplyAccountErasuresImpl(
                erasedAccountDataSource,
                accountDataSource,
                accountEraser,
                eventProducer,
                transactionManager,
            )
    }

    @Test
    fun `should erase an account a restore brought back`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = Authentication.stub()
        coEvery { fixture.erasedAccountDataSource.getReapplyRequested() } returns listOf(account.userId)
        coEvery { fixture.erasedAccountDataSource.claimReapply(account.userId) } returns true
        coEvery { fixture.accountDataSource.getAuthRecordByUserId(account.userId) } returns account

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.accountEraser.erase(account.id, account.userId) }
    }

    @Test
    fun `should still tell other services when the account itself is already gone`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        coEvery { fixture.erasedAccountDataSource.getReapplyRequested() } returns listOf(userId)
        coEvery { fixture.erasedAccountDataSource.claimReapply(userId) } returns true
        coEvery { fixture.accountDataSource.getAuthRecordByUserId(userId) } returns null

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.eventProducer.send(match<AuthEvent.UserDeleted> { it.userId == userId }, any())
        }
        coVerify(exactly = 0) { fixture.accountEraser.erase(any(), any()) }
    }

    @Test
    fun `should leave an erasure alone when another instance already claimed it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        coEvery { fixture.erasedAccountDataSource.getReapplyRequested() } returns listOf(userId)
        coEvery { fixture.erasedAccountDataSource.claimReapply(userId) } returns false

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.accountDataSource.getAuthRecordByUserId(any()) }
        coVerify(exactly = 0) { fixture.accountEraser.erase(any(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any(), any()) }
    }

    @Test
    fun `should claim the erasure before reading the account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = Authentication.stub()
        coEvery { fixture.erasedAccountDataSource.getReapplyRequested() } returns listOf(account.userId)
        coEvery { fixture.erasedAccountDataSource.claimReapply(account.userId) } returns true
        coEvery { fixture.accountDataSource.getAuthRecordByUserId(account.userId) } returns account

        whenn()
        fixture.sut()

        then()
        coVerifyOrder {
            fixture.erasedAccountDataSource.claimReapply(account.userId)
            fixture.accountDataSource.getAuthRecordByUserId(account.userId)
            fixture.accountEraser.erase(account.id, account.userId)
        }
    }

    @Test
    fun `should claim and erase within one transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = Authentication.stub()
        val claimedInTransaction = mutableListOf<Boolean>()
        coEvery { fixture.erasedAccountDataSource.getReapplyRequested() } returns listOf(account.userId)
        coEvery { fixture.erasedAccountDataSource.claimReapply(account.userId) } coAnswers {
            claimedInTransaction += fixture.transactionManager.isInTransaction()
            true
        }
        coEvery { fixture.accountDataSource.getAuthRecordByUserId(account.userId) } returns account

        whenn()
        fixture.sut()

        then()
        assertEquals(listOf(true), claimedInTransaction)
    }

    @Test
    fun `should succeed when no restore asked for anything`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.erasedAccountDataSource.getReapplyRequested() } returns emptyList()

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.erasedAccountDataSource.claimReapply(any()) }
    }

    @Test
    fun `should still erase the other accounts when one fails and then report the failure`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val failing = Authentication.stub()
        val next = Authentication.stub()
        val error = RuntimeException("erasure failed")
        coEvery { fixture.erasedAccountDataSource.getReapplyRequested() } returns listOf(failing.userId, next.userId)
        coEvery { fixture.erasedAccountDataSource.claimReapply(any()) } returns true
        coEvery { fixture.accountDataSource.getAuthRecordByUserId(failing.userId) } returns failing
        coEvery { fixture.accountDataSource.getAuthRecordByUserId(next.userId) } returns next
        coEvery { fixture.accountEraser.erase(failing.id, failing.userId) } throws error

        whenn()
        val result = fixture.sut()

        then()
        assertSame(error, result.exceptionOrNull())
        coVerify(exactly = 1) { fixture.accountEraser.erase(next.id, next.userId) }
    }
}
