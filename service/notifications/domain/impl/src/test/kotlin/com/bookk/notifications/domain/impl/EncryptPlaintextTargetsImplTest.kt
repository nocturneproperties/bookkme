package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EncryptPlaintextTargetsImplTest {

    private class SutFixture {
        val targetDataSource = mockk<NotificationTargetDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = EncryptPlaintextTargetsImpl(targetDataSource, transactionManager)
        val batchesInTransaction = mutableListOf<Boolean>()

        init {
            transactionManager.mockTransaction()
        }

        fun encryptEmailBatches(vararg sizes: Int) {
            val remaining = sizes.toMutableList()
            coEvery { targetDataSource.encryptPlaintextEmails(any()) } coAnswers {
                batchesInTransaction += transactionManager.isInTransaction()
                remaining.removeFirst()
            }
        }

        fun encryptTelegramBatches(vararg sizes: Int) {
            val remaining = sizes.toMutableList()
            coEvery { targetDataSource.encryptPlaintextTelegramTags(any()) } coAnswers {
                batchesInTransaction += transactionManager.isInTransaction()
                remaining.removeFirst()
            }
        }
    }

    @Test
    fun `should encrypt email batches until one comes back short`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.encryptEmailBatches(BATCH, BATCH, 3)
        fixture.encryptTelegramBatches(0)

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 3) { fixture.targetDataSource.encryptPlaintextEmails(BATCH) }
    }

    @Test
    fun `should encrypt telegram batches until one comes back short`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.encryptEmailBatches(0)
        fixture.encryptTelegramBatches(BATCH, 7)

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 2) { fixture.targetDataSource.encryptPlaintextTelegramTags(BATCH) }
    }

    @Test
    fun `should encrypt every batch in a transaction of its own`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.encryptEmailBatches(BATCH, 0)
        fixture.encryptTelegramBatches(0)

        whenn()
        fixture.sut.invoke()

        then()
        assertEquals(listOf(true, true, true), fixture.batchesInTransaction)
    }

    @Test
    fun `should stop after one query per table when no plaintext target is left`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.encryptEmailBatches(0)
        fixture.encryptTelegramBatches(0)

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 1) { fixture.targetDataSource.encryptPlaintextEmails(BATCH) }
        coVerify(exactly = 1) { fixture.targetDataSource.encryptPlaintextTelegramTags(BATCH) }
    }

    @Test
    fun `should fail without touching telegram targets when an email batch fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val error = IllegalStateException("The stored target has a shredded key")
        coEvery { fixture.targetDataSource.encryptPlaintextEmails(any()) } throws error

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertSame(error, result.exceptionOrNull())
        coVerify(exactly = 0) { fixture.targetDataSource.encryptPlaintextTelegramTags(any()) }
    }

    private companion object {
        const val BATCH = 100
    }
}
