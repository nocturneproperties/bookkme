package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.datasource.SavedBusinessDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class RemoveSavedBusinessImplTest {

    private class SutFixture {
        val savedBusinessDataSource = mockk<SavedBusinessDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = RemoveSavedBusinessImpl(savedBusinessDataSource, transactionManager)
    }

    @Test
    fun `should remove the business from the caller's saved businesses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val removedInTransaction = mutableListOf<Boolean>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { savedBusinessDataSource.remove(userId, businessId) } coAnswers {
                removedInTransaction += transactionManager.isInTransaction()
            }
        }

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.savedBusinessDataSource.remove(userId, businessId) }
        assertTrue(removedInTransaction.single())
    }
}
