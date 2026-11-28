package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.SavedBusiness
import com.bookk.appointments.domain.datasource.SavedBusinessDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetSavedBusinessesImplTest {

    private class SutFixture {
        val savedBusinessDataSource = mockk<SavedBusinessDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetSavedBusinessesImpl(savedBusinessDataSource, transactionManager)
    }

    @Test
    fun `should return the caller's saved businesses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val saved = listOf(SavedBusiness.stub(), SavedBusiness.stub(enabled = false))
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { savedBusinessDataSource.getAll(userId) } returns saved
        }

        whenn()
        val result = fixture.sut(userId)

        then()
        assertEquals(saved, result.getOrThrow())
    }

    @Test
    fun `should return an empty list for a caller without saved businesses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { savedBusinessDataSource.getAll(userId) } returns emptyList()
        }

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.getOrThrow().isEmpty())
    }
}
