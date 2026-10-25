package library.signing.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import library.signing.SigningKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class GetVerificationKeysImplTest {

    private class SutFixture {
        val signingKeyDataSource = mockk<SigningKeyDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetVerificationKeysImpl(signingKeyDataSource, transactionManager)
    }

    @Test
    fun `should return verification keys from datasource`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val keys = listOf(SigningKey.stub(), SigningKey.stub())
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { signingKeyDataSource.getVerificationKeys() } returns keys
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess)
        assertEquals(keys, result.getOrNull())
    }

    @Test
    fun `should return empty list when no verification keys exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { signingKeyDataSource.getVerificationKeys() } returns emptyList()
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess)
        assertEquals(emptyList<SigningKey>(), result.getOrNull())
    }
}
