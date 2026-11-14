package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.datasource.UserDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EncryptPlaintextProfilesImplTest {

    private class SutFixture {
        val userDataSource = mockk<UserDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = EncryptPlaintextProfilesImpl(userDataSource, transactionManager)
        val batchesInTransaction = mutableListOf<Boolean>()

        init {
            transactionManager.mockTransaction()
        }

        fun encryptBatches(vararg sizes: Int) {
            val remaining = sizes.toMutableList()
            coEvery { userDataSource.encryptPlaintextProfiles(any()) } coAnswers {
                batchesInTransaction += transactionManager.isInTransaction()
                remaining.removeFirst()
            }
        }
    }

    @Test
    fun `should encrypt batches until one comes back short`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.encryptBatches(BATCH, BATCH, 3)

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 3) { fixture.userDataSource.encryptPlaintextProfiles(BATCH) }
    }

    @Test
    fun `should encrypt every batch in a transaction of its own`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.encryptBatches(BATCH, 0)

        whenn()
        fixture.sut.invoke()

        then()
        assertEquals(listOf(true, true), fixture.batchesInTransaction)
    }

    @Test
    fun `should stop after a single query when no plaintext profile is left`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.encryptBatches(0)

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 1) { fixture.userDataSource.encryptPlaintextProfiles(BATCH) }
    }

    @Test
    fun `should fail when a batch fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val error = IllegalStateException("The stored user has a shredded key")
        coEvery { fixture.userDataSource.encryptPlaintextProfiles(any()) } throws error

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertSame(error, result.exceptionOrNull())
    }

    private companion object {
        const val BATCH = 100
    }
}
