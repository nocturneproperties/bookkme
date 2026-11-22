package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.BusinessLink
import com.bookk.business.domain.api.business.operation.SetBusinessLink
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessLinkDataSource
import com.bookk.business.domain.datasource.LockedBusiness
import com.bookk.business.domain.datasource.RowLock
import com.bookk.business.domain.datasource.StoredBusinessLink
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.TestClock
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

internal class SetBusinessLinkImplTest {

    private class SutFixture {
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val businessDataSource = mockk<BusinessDataSource>()
        val businessLinkDataSource = mockk<BusinessLinkDataSource>(relaxUnitFun = true)
        val transactionManager = mockk<TransactionManager>()
        val clock = TestClock()
        val sut = SetBusinessLinkImpl(businessDataSource, businessLinkDataSource, transactionManager, clock)

        fun ownedBusiness() {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness.stub()
            coEvery { businessDataSource.isOwner(userId, businessId) } returns true
        }

        fun storedName(name: String, stored: StoredBusinessLink?) {
            coEvery { businessLinkDataSource.getLinkByNameForUpdate(name) } returns stored
        }

        fun currentLink(name: String?) {
            coEvery { businessLinkDataSource.getCurrentLink(businessId) } returns
                name?.let { BusinessLink(businessId, it) }
        }

        fun recentChanges(count: Long) {
            coEvery { businessLinkDataSource.countLinkChangesSince(businessId, clock.now() - 90.days) } returns count
        }
    }

    @Test
    fun `should create the link when the business has none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            ownedBusiness()
            storedName("my-shop", null)
            currentLink(null)
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, "my-shop")

        then()
        assertEquals(BusinessLink(fixture.businessId, "my-shop"), result.getOrNull())
        coVerify(exactly = 1) { fixture.businessLinkDataSource.createLink(fixture.businessId, "my-shop") }
        coVerify(exactly = 0) { fixture.businessLinkDataSource.releaseCurrentLink(any(), any()) }
        coVerify(exactly = 0) { fixture.businessLinkDataSource.recordLinkChange(any(), any()) }
    }

    @Test
    fun `should store the name in lowercase without surrounding spaces`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            ownedBusiness()
            storedName("my-shop", null)
            currentLink(null)
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, "  My-Shop ")

        then()
        assertEquals("my-shop", result.getOrNull()?.name)
        coVerify(exactly = 1) { fixture.businessLinkDataSource.createLink(fixture.businessId, "my-shop") }
    }

    @Test
    fun `should lock the business and then the name before any plain read`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            ownedBusiness()
            storedName("my-shop", null)
            currentLink(null)
        }

        whenn()
        fixture.sut(fixture.userId, fixture.businessId, "my-shop")

        then()
        coVerifyOrder {
            fixture.businessDataSource.lockBusiness(fixture.businessId, RowLock.EXCLUSIVE)
            fixture.businessLinkDataSource.getLinkByNameForUpdate("my-shop")
            fixture.businessDataSource.isOwner(fixture.userId, fixture.businessId)
        }
    }

    @Test
    fun `should return failure when the name is not a valid link name`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.ownedBusiness()
        val invalidNames = listOf("ab", "a".repeat(33), "my_shop", "-shop", "shop-", "my--shop", "café", "my shop", "")

        whenn()
        val results = invalidNames.map { fixture.sut(fixture.userId, fixture.businessId, it) }

        then()
        assertTrue(results.all { it.exceptionOrNull() is SetBusinessLink.Error.LinkInvalid })
        coVerify(exactly = 0) { fixture.businessLinkDataSource.createLink(any(), any()) }
    }

    @Test
    fun `should accept names of the shortest and longest allowed length`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            ownedBusiness()
            coEvery { businessLinkDataSource.getLinkByNameForUpdate(any()) } returns null
            currentLink(null)
        }

        whenn()
        val shortest = fixture.sut(fixture.userId, fixture.businessId, "a1b")
        val longest = fixture.sut(fixture.userId, fixture.businessId, "a".repeat(32))

        then()
        assertTrue(shortest.isSuccess)
        assertTrue(longest.isSuccess)
    }

    @Test
    fun `should return failure when the name is reserved`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.ownedBusiness()

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, "Api")

        then()
        assertTrue(result.exceptionOrNull() is SetBusinessLink.Error.LinkReserved)
        coVerify(exactly = 0) { fixture.businessLinkDataSource.createLink(any(), any()) }
    }

    @Test
    fun `should return failure when the business does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns null
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, "my-shop")

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        coVerify(exactly = 0) { fixture.businessLinkDataSource.createLink(any(), any()) }
    }

    @Test
    fun `should return failure without changing anything when the caller is not the owner`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness.stub()
            coEvery { businessDataSource.isOwner(userId, businessId) } returns false
            storedName("my-shop", StoredBusinessLink.stub(name = "my-shop", releasedAt = clock.now() - 100.days))
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, "my-shop")

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.businessLinkDataSource.deleteLink(any()) }
        coVerify(exactly = 0) { fixture.businessLinkDataSource.createLink(any(), any()) }
    }

    @Test
    fun `should return failure when another business uses the name`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            ownedBusiness()
            storedName("my-shop", StoredBusinessLink.stub(name = "my-shop"))
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, "my-shop")

        then()
        assertTrue(result.exceptionOrNull() is SetBusinessLink.Error.LinkTaken)
        coVerify(exactly = 0) { fixture.businessLinkDataSource.createLink(any(), any()) }
    }

    @Test
    fun `should return failure when another business still holds the name it used before`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            ownedBusiness()
            storedName("my-shop", StoredBusinessLink.stub(name = "my-shop", releasedAt = clock.now() - 89.days))
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, "my-shop")

        then()
        assertTrue(result.exceptionOrNull() is SetBusinessLink.Error.LinkTaken)
        coVerify(exactly = 0) { fixture.businessLinkDataSource.createLink(any(), any()) }
    }

    @Test
    fun `should take a name whose hold by another business has ended before the cleanup job ran`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            ownedBusiness()
            storedName("my-shop", StoredBusinessLink.stub(name = "my-shop", releasedAt = clock.now() - 91.days))
            currentLink(null)
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, "my-shop")

        then()
        assertEquals(BusinessLink(fixture.businessId, "my-shop"), result.getOrNull())
        coVerifyOrder {
            fixture.businessLinkDataSource.deleteLink("my-shop")
            fixture.businessLinkDataSource.createLink(fixture.businessId, "my-shop")
        }
    }

    @Test
    fun `should return failure when another business claims the name at the same time`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            ownedBusiness()
            storedName("my-shop", null)
            currentLink(null)
            coEvery {
                businessLinkDataSource.createLink(businessId, "my-shop")
            } throws Error.UniqueConstraintFailed("duplicate", IllegalStateException())
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, "my-shop")

        then()
        assertTrue(result.exceptionOrNull() is SetBusinessLink.Error.LinkTaken)
    }

    @Test
    fun `should change nothing when the name is already the business's link`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            ownedBusiness()
            storedName("my-shop", StoredBusinessLink.stub(businessId = businessId, name = "my-shop"))
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, "my-shop")

        then()
        assertEquals(BusinessLink(fixture.businessId, "my-shop"), result.getOrNull())
        coVerify(exactly = 0) { fixture.businessLinkDataSource.createLink(any(), any()) }
        coVerify(exactly = 0) { fixture.businessLinkDataSource.releaseCurrentLink(any(), any()) }
        coVerify(exactly = 0) { fixture.businessLinkDataSource.restoreLink(any(), any()) }
        coVerify(exactly = 0) { fixture.businessLinkDataSource.recordLinkChange(any(), any()) }
    }

    @Test
    fun `should release the current link, record the change and create the new one when renaming`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            ownedBusiness()
            storedName("new-shop", null)
            currentLink("old-shop")
            recentChanges(2)
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, "new-shop")

        then()
        assertEquals(BusinessLink(fixture.businessId, "new-shop"), result.getOrNull())
        coVerifyOrder {
            fixture.businessLinkDataSource.releaseCurrentLink(fixture.businessId, fixture.clock.now())
            fixture.businessLinkDataSource.recordLinkChange(fixture.businessId, fixture.clock.now())
            fixture.businessLinkDataSource.createLink(fixture.businessId, "new-shop")
        }
    }

    @Test
    fun `should return failure when renaming after 3 changes within 90 days`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            ownedBusiness()
            storedName("new-shop", null)
            currentLink("old-shop")
            recentChanges(3)
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, "new-shop")

        then()
        assertTrue(result.exceptionOrNull() is SetBusinessLink.Error.ChangeLimitReached)
        coVerify(exactly = 0) { fixture.businessLinkDataSource.releaseCurrentLink(any(), any()) }
        coVerify(exactly = 0) { fixture.businessLinkDataSource.createLink(any(), any()) }
    }

    @Test
    fun `should switch back to a name the business still holds and record the change`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            ownedBusiness()
            storedName(
                "old-shop",
                StoredBusinessLink.stub(businessId = businessId, name = "old-shop", releasedAt = clock.now() - 10.days),
            )
            currentLink("new-shop")
            recentChanges(1)
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, "old-shop")

        then()
        assertEquals(BusinessLink(fixture.businessId, "old-shop"), result.getOrNull())
        coVerifyOrder {
            fixture.businessLinkDataSource.releaseCurrentLink(fixture.businessId, fixture.clock.now())
            fixture.businessLinkDataSource.recordLinkChange(fixture.businessId, fixture.clock.now())
            fixture.businessLinkDataSource.restoreLink(fixture.businessId, "old-shop")
        }
        coVerify(exactly = 0) { fixture.businessLinkDataSource.createLink(any(), any()) }
    }

    @Test
    fun `should return failure when switching back after 3 changes within 90 days`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            ownedBusiness()
            storedName(
                "old-shop",
                StoredBusinessLink.stub(businessId = businessId, name = "old-shop", releasedAt = clock.now() - 10.days),
            )
            currentLink("new-shop")
            recentChanges(3)
        }

        whenn()
        val result = fixture.sut(fixture.userId, fixture.businessId, "old-shop")

        then()
        assertTrue(result.exceptionOrNull() is SetBusinessLink.Error.ChangeLimitReached)
        coVerify(exactly = 0) { fixture.businessLinkDataSource.releaseCurrentLink(any(), any()) }
        coVerify(exactly = 0) { fixture.businessLinkDataSource.restoreLink(any(), any()) }
    }
}
