package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.datasource.BusinessLinkDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.TestClock
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days

internal class DeleteReleasedBusinessLinksImplTest {

    private class SutFixture {
        val businessLinkDataSource = mockk<BusinessLinkDataSource>(relaxUnitFun = true)
        val transactionManager = mockk<TransactionManager>().apply { mockTransaction() }
        val clock = TestClock()
        val sut = DeleteReleasedBusinessLinksImpl(businessLinkDataSource, transactionManager, clock)
    }

    @Test
    fun `should delete the names released and the changes made more than 90 days ago`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.businessLinkDataSource.deleteLinksReleasedBefore(fixture.clock.now() - 90.days)
        }
        coVerify(exactly = 1) {
            fixture.businessLinkDataSource.deleteLinkChangesBefore(fixture.clock.now() - 90.days)
        }
    }
}
