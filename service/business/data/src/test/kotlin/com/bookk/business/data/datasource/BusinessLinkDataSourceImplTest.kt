package com.bookk.business.data.datasource

import com.bookk.business.data.orm.table.BusinessDashboardTable
import com.bookk.business.data.orm.table.BusinessDayOffTable
import com.bookk.business.data.orm.table.BusinessLinkChangeTable
import com.bookk.business.data.orm.table.BusinessLinkTable
import com.bookk.business.data.orm.table.BusinessPermissionGrantsTable
import com.bookk.business.data.orm.table.BusinessTable
import com.bookk.business.data.orm.table.BusinessWorkingHoursTable
import com.bookk.business.domain.api.business.entity.BusinessLink
import com.bookk.business.domain.datasource.StoredBusinessLink
import com.bookk.core.data.map.toDomain
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class BusinessLinkDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(
            BusinessTable,
            BusinessDashboardTable,
            BusinessPermissionGrantsTable,
            BusinessWorkingHoursTable,
            BusinessDayOffTable,
            BusinessLinkTable,
            BusinessLinkChangeTable,
        )
        val sut = BusinessLinkDataSourceImpl()
        val businessSut = BusinessDataSourceImpl()
        val now = Instant.parse("2026-11-22T10:00:00Z")

        suspend fun createBusiness(): Uuid {
            return suspendTransaction {
                businessSut.createBusiness(Uuid.random(), "Test Business", "USD", TimeZone.UTC)
            }.id
        }

        suspend fun linkWithHeldName(businessId: Uuid, heldName: String, releasedAt: Instant, currentName: String) {
            suspendTransaction {
                sut.createLink(businessId, heldName)
                sut.releaseCurrentLink(businessId, releasedAt)
                sut.createLink(businessId, currentName)
            }
        }
    }

    @Test
    fun `should find a created link by name as the business's current link`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.createBusiness()

        whenn()
        suspendTransaction { fixture.sut.createLink(businessId, "my-shop") }
        val byName = suspendTransaction { fixture.sut.getLinkByName("my-shop") }
        val current = suspendTransaction { fixture.sut.getCurrentLink(businessId) }

        then()
        assertEquals(StoredBusinessLink(businessId, "my-shop", releasedAt = null), byName)
        assertEquals(BusinessLink(businessId, "my-shop"), current)
    }

    @Test
    fun `should return nothing for a name no business uses`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val byName = suspendTransaction { fixture.sut.getLinkByName("my-shop") }

        then()
        assertNull(byName)
    }

    @Test
    fun `should return no current link for a business without one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.createBusiness()

        whenn()
        val current = suspendTransaction { fixture.sut.getCurrentLink(businessId) }

        then()
        assertNull(current)
    }

    @Test
    fun `should keep a released name held by its business while the new one is current`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.createBusiness()

        whenn()
        fixture.linkWithHeldName(businessId, "old-shop", fixture.now, "new-shop")
        val held = suspendTransaction { fixture.sut.getLinkByName("old-shop") }
        val current = suspendTransaction { fixture.sut.getCurrentLink(businessId) }

        then()
        assertEquals(StoredBusinessLink(businessId, "old-shop", releasedAt = fixture.now), held)
        assertEquals(BusinessLink(businessId, "new-shop"), current)
    }

    @Test
    fun `should refuse a name another business already uses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.createBusiness()
        val otherBusinessId = fixture.createBusiness()
        suspendTransaction { fixture.sut.createLink(businessId, "my-shop") }

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.createLink(otherBusinessId, "my-shop") } }

        then()
        assertTrue(result.exceptionOrNull()?.toDomain() is Error.UniqueConstraintFailed)
    }

    @Test
    fun `should count only the business's changes since the given time`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.createBusiness()
        val otherBusinessId = fixture.createBusiness()
        suspendTransaction {
            fixture.sut.recordLinkChange(businessId, fixture.now - 100.days)
            fixture.sut.recordLinkChange(businessId, fixture.now - 10.days)
            fixture.sut.recordLinkChange(businessId, fixture.now)
            fixture.sut.recordLinkChange(otherBusinessId, fixture.now)
        }

        whenn()
        val count = suspendTransaction { fixture.sut.countLinkChangesSince(businessId, fixture.now - 90.days) }

        then()
        assertEquals(2, count)
    }

    @Test
    fun `should delete only changes made before the cutoff`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.createBusiness()
        suspendTransaction {
            fixture.sut.recordLinkChange(businessId, fixture.now - 91.days)
            fixture.sut.recordLinkChange(businessId, fixture.now - 89.days)
        }

        whenn()
        suspendTransaction { fixture.sut.deleteLinkChangesBefore(fixture.now - 90.days) }
        val count = suspendTransaction { fixture.sut.countLinkChangesSince(businessId, fixture.now - 1000.days) }

        then()
        assertEquals(1, count)
    }

    @Test
    fun `should read a name with the locking read like the plain one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.createBusiness()
        fixture.linkWithHeldName(businessId, "old-shop", fixture.now, "new-shop")

        whenn()
        val held = suspendTransaction { fixture.sut.getLinkByNameForUpdate("old-shop") }
        val missing = suspendTransaction { fixture.sut.getLinkByNameForUpdate("my-shop") }

        then()
        assertEquals(StoredBusinessLink(businessId, "old-shop", releasedAt = fixture.now), held)
        assertNull(missing)
    }

    @Test
    fun `should delete a name so another business can take it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.createBusiness()
        val otherBusinessId = fixture.createBusiness()
        fixture.linkWithHeldName(businessId, "old-shop", fixture.now - 100.days, "new-shop")

        whenn()
        suspendTransaction {
            fixture.sut.deleteLink("old-shop")
            fixture.sut.createLink(otherBusinessId, "old-shop")
        }
        val taken = suspendTransaction { fixture.sut.getLinkByName("old-shop") }

        then()
        assertEquals(StoredBusinessLink(otherBusinessId, "old-shop", releasedAt = null), taken)
    }

    @Test
    fun `should make a held name current again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.createBusiness()
        fixture.linkWithHeldName(businessId, "old-shop", fixture.now, "new-shop")

        whenn()
        suspendTransaction {
            fixture.sut.releaseCurrentLink(businessId, fixture.now)
            fixture.sut.restoreLink(businessId, "old-shop")
        }
        val current = suspendTransaction { fixture.sut.getCurrentLink(businessId) }
        val released = suspendTransaction { fixture.sut.getLinkByName("new-shop") }

        then()
        assertEquals(BusinessLink(businessId, "old-shop"), current)
        assertEquals(fixture.now, released?.releasedAt)
    }

    @Test
    fun `should not restore a name held by another business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.createBusiness()
        val otherBusinessId = fixture.createBusiness()
        fixture.linkWithHeldName(otherBusinessId, "old-shop", fixture.now, "new-shop")

        whenn()
        suspendTransaction { fixture.sut.restoreLink(businessId, "old-shop") }
        val held = suspendTransaction { fixture.sut.getLinkByName("old-shop") }

        then()
        assertEquals(StoredBusinessLink(otherBusinessId, "old-shop", releasedAt = fixture.now), held)
    }

    @Test
    fun `should delete only names released before the cutoff`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.createBusiness()
        suspendTransaction {
            fixture.sut.createLink(businessId, "expired")
            fixture.sut.releaseCurrentLink(businessId, fixture.now - 91.days)
            fixture.sut.createLink(businessId, "held")
            fixture.sut.releaseCurrentLink(businessId, fixture.now - 89.days)
            fixture.sut.createLink(businessId, "current")
        }

        whenn()
        suspendTransaction { fixture.sut.deleteLinksReleasedBefore(fixture.now - 90.days) }
        val expired = suspendTransaction { fixture.sut.getLinkByName("expired") }
        val held = suspendTransaction { fixture.sut.getLinkByName("held") }
        val current = suspendTransaction { fixture.sut.getCurrentLink(businessId) }

        then()
        assertNull(expired)
        assertEquals("held", held?.name)
        assertEquals(BusinessLink(businessId, "current"), current)
    }

    @Test
    fun `should delete a business's links and changes with the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.createBusiness()
        fixture.linkWithHeldName(businessId, "old-shop", fixture.now, "new-shop")
        suspendTransaction { fixture.sut.recordLinkChange(businessId, fixture.now) }

        whenn()
        suspendTransaction { fixture.businessSut.deleteBusinesses(listOf(businessId)) }
        val held = suspendTransaction { fixture.sut.getLinkByName("old-shop") }
        val current = suspendTransaction { fixture.sut.getLinkByName("new-shop") }
        val changes = suspendTransaction { fixture.sut.countLinkChangesSince(businessId, fixture.now - 1.days) }

        then()
        assertNull(held)
        assertNull(current)
        assertEquals(0, changes)
    }
}
