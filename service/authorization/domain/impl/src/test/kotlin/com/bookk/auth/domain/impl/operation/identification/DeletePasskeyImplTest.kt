package com.bookk.auth.domain.impl.operation.identification

import com.bookk.auth.domain.api.identification.operation.DeletePasskey
import com.bookk.auth.domain.datasource.PassKeyDataSource
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

internal class DeletePasskeyImplTest {

    private class SutFixture {
        val passKeyDataSource = mockk<PassKeyDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = DeletePasskeyImpl(passKeyDataSource, transactionManager)
    }

    @Test
    fun `should delete passkey successfully when more than one passkey exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = Uuid.random()
        val authId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { passKeyDataSource.deletePasskey(id, authId) } returns 1
        }

        whenn()
        val result = fixture.sut.invoke(id, authId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.passKeyDataSource.deletePasskey(id, authId) }
    }

    @Test
    fun `should return LastPasskey error when no rows were deleted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = Uuid.random()
        val authId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { passKeyDataSource.deletePasskey(id, authId) } returns 0
        }

        whenn()
        val result = fixture.sut.invoke(id, authId)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DeletePasskey.Error.LastPasskey)
    }
}
