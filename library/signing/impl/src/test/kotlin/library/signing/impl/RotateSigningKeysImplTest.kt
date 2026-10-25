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
import library.signing.SigningKey
import library.signing.SigningKeyStatus
import library.signing.impl.key.SigningKeyPairGenerator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal class RotateSigningKeysImplTest {

    private class SutFixture {
        val signingKeyDataSource = mockk<SigningKeyDataSource>()
        val keyPairGenerator = mockk<SigningKeyPairGenerator>()
        val transactionManager = mockk<TransactionManager>()
        val sut = RotateSigningKeysImpl(signingKeyDataSource, keyPairGenerator, transactionManager)
    }

    @Test
    fun `should demote the current active key and insert a new active key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val previousActive = SigningKey.stub()
        with(fixture) {
            coEvery { signingKeyDataSource.getActiveKey() } returns previousActive
            every { keyPairGenerator.generate() } returns ("generated-public" to "generated-private")
            coEvery { signingKeyDataSource.insertKey(any(), any()) } returns SigningKey.stub()
            coEvery { signingKeyDataSource.updateStatus(any(), any(), any()) } returns Unit
            coEvery { signingKeyDataSource.deleteRetiredBefore(any<Instant>()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(retireInterval = 1.days)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.signingKeyDataSource.insertKey("generated-public", "generated-private") }
        coVerify(exactly = 1) {
            fixture.signingKeyDataSource.updateStatus(previousActive.id, SigningKeyStatus.RETIRING, any())
        }
        coVerify(exactly = 1) { fixture.signingKeyDataSource.deleteRetiredBefore(any()) }
    }

    @Test
    fun `should only insert a new active key when none exists yet`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            coEvery { signingKeyDataSource.getActiveKey() } returns null
            every { keyPairGenerator.generate() } returns ("generated-public" to "generated-private")
            coEvery { signingKeyDataSource.insertKey(any(), any()) } returns SigningKey.stub()
            coEvery { signingKeyDataSource.deleteRetiredBefore(any<Instant>()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke(retireInterval = 1.days)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.signingKeyDataSource.insertKey("generated-public", "generated-private") }
        coVerify(exactly = 0) { fixture.signingKeyDataSource.updateStatus(any(), any(), any()) }
    }
}
