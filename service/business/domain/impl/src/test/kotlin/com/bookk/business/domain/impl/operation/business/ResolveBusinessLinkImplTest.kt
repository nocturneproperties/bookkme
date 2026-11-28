package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.BusinessLink
import com.bookk.business.domain.api.business.operation.ResolveBusinessLink
import com.bookk.business.domain.datasource.BusinessLinkDataSource
import com.bookk.business.domain.datasource.StoredBusinessLink
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.TestClock
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

internal class ResolveBusinessLinkImplTest {

    private class SutFixture {
        val businessId = Uuid.random()
        val businessLinkDataSource = mockk<BusinessLinkDataSource>()
        val transactionManager = mockk<TransactionManager>().apply { mockTransaction() }
        val clock = TestClock()
        val sut = ResolveBusinessLinkImpl(businessLinkDataSource, transactionManager, clock)
    }

    @Test
    fun `should return the business that uses the name`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            coEvery {
                businessLinkDataSource.getLinkByName("my-shop")
            } returns StoredBusinessLink.stub(businessId = businessId, name = "my-shop")
        }

        whenn()
        val result = fixture.sut("my-shop")

        then()
        assertEquals(BusinessLink(fixture.businessId, "my-shop"), result.getOrNull())
    }

    @Test
    fun `should look the name up in lowercase without surrounding spaces`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            coEvery {
                businessLinkDataSource.getLinkByName("my-shop")
            } returns StoredBusinessLink.stub(businessId = businessId, name = "my-shop")
        }

        whenn()
        val result = fixture.sut(" My-Shop ")

        then()
        assertEquals(BusinessLink(fixture.businessId, "my-shop"), result.getOrNull())
    }

    @Test
    fun `should return the business's current link for a name it used before`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            coEvery { businessLinkDataSource.getLinkByName("old-shop") } returns StoredBusinessLink.stub(
                businessId = businessId,
                name = "old-shop",
                releasedAt = clock.now() - 89.days,
            )
            coEvery { businessLinkDataSource.getCurrentLink(businessId) } returns BusinessLink(businessId, "new-shop")
        }

        whenn()
        val result = fixture.sut("old-shop")

        then()
        assertEquals(BusinessLink(fixture.businessId, "new-shop"), result.getOrNull())
    }

    @Test
    fun `should return failure for a name whose hold ended before the cleanup job ran`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            coEvery { businessLinkDataSource.getLinkByName("old-shop") } returns StoredBusinessLink.stub(
                businessId = businessId,
                name = "old-shop",
                releasedAt = clock.now() - 91.days,
            )
        }

        whenn()
        val result = fixture.sut("old-shop")

        then()
        assertTrue(result.exceptionOrNull() is ResolveBusinessLink.Error.LinkNotFound)
    }

    @Test
    fun `should return failure without a lookup for a name no business can have`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut("a".repeat(500))

        then()
        assertTrue(result.exceptionOrNull() is ResolveBusinessLink.Error.LinkNotFound)
        coVerify(exactly = 0) { fixture.businessLinkDataSource.getLinkByName(any()) }
    }

    @Test
    fun `should return failure when no business uses the name`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.businessLinkDataSource.getLinkByName("my-shop") } returns null

        whenn()
        val result = fixture.sut("my-shop")

        then()
        assertTrue(result.exceptionOrNull() is ResolveBusinessLink.Error.LinkNotFound)
    }
}
