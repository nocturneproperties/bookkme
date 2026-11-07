package library.export.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.TestClock
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class DeleteExpiredDataExportsImplTest {

    private class SutFixture {
        val dataSource = mockk<DataExportDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val clock = TestClock()
        val sut = DeleteExpiredDataExportsImpl(transactionManager, dataSource, clock)
        val deletedBefore = mutableListOf<Pair<Instant, Boolean>>()

        init {
            transactionManager.mockTransaction()
            coEvery { dataSource.deleteExpired(any()) } coAnswers {
                deletedBefore += firstArg<Instant>() to transactionManager.isInTransaction()
            }
        }
    }

    @Test
    fun `should delete exports that expired by now inside a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        assertEquals(listOf(fixture.clock.now to true), fixture.deletedBefore)
    }

    @Test
    fun `should fail when the expired exports can't be deleted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.dataSource.deleteExpired(any()) } throws IllegalStateException("database unreachable")

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isFailure)
    }
}
