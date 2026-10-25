package library.signing.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import library.signing.SigningKey
import library.signing.impl.key.SigningKeyPairGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class GetActiveSigningKeyImplTest {

    private class SutFixture {
        val signingKeyDataSource = mockk<SigningKeyDataSource>()
        val keyPairGenerator = mockk<SigningKeyPairGenerator>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetActiveSigningKeyImpl(signingKeyDataSource, keyPairGenerator, transactionManager)
    }

    @Test
    fun `should return the existing active key without creating a new one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val activeKey = SigningKey.stub()
        with(fixture) {
            coEvery { signingKeyDataSource.getActiveKey() } returns activeKey
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertEquals(activeKey, result.getOrThrow())
        verify(exactly = 0) { fixture.keyPairGenerator.generate() }
        coVerify(exactly = 0) { fixture.signingKeyDataSource.insertKey(any(), any()) }
    }

    @Test
    fun `should generate and insert a new key when none exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val generatedKey = SigningKey.stub(publicKeyPem = "generated-public", privateKeyPem = "generated-private")
        with(fixture) {
            coEvery { signingKeyDataSource.getActiveKey() } returns null
            every { keyPairGenerator.generate() } returns ("generated-public" to "generated-private")
            coEvery { signingKeyDataSource.insertKey(any(), any()) } returns generatedKey
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertEquals(generatedKey, result.getOrThrow())
        coVerify(exactly = 1) { fixture.signingKeyDataSource.insertKey("generated-public", "generated-private") }
    }
}
