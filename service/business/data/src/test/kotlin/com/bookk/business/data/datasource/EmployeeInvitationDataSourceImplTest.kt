package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.EmployeeInvitationEntity
import com.bookk.business.data.orm.table.BusinessDashboardTable
import com.bookk.business.data.orm.table.BusinessDayOffTable
import com.bookk.business.data.orm.table.BusinessPermissionGrantsTable
import com.bookk.business.data.orm.table.BusinessTable
import com.bookk.business.data.orm.table.BusinessWorkingHoursTable
import com.bookk.business.data.orm.table.EmployeeInvitationTable
import com.bookk.business.domain.api.employee.entity.EmployeeInvitation
import com.bookk.business.domain.api.employee.entity.EmployeeInvitationStatus
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class EmployeeInvitationDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(
            BusinessTable, BusinessDashboardTable, BusinessPermissionGrantsTable, BusinessWorkingHoursTable, BusinessDayOffTable, EmployeeInvitationTable
        )
        val sut = EmployeeInvitationDataSourceImpl()
        val businessSut = BusinessDataSourceImpl()
        lateinit var businessId: Uuid

        suspend fun setup() {
            businessId = suspendTransaction {
                businessSut.createBusiness(Uuid.random(), "Test Business", "USD", TimeZone.UTC)
            }.id
        }
    }

    @Test
    fun `should create invitation in pending status`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val invitedBy = Uuid.random()
        val invitation = EmployeeInvitation.stub(
            businessId = fixture.businessId,
            invitedBy = invitedBy,
            code = "AAAA1111"
        )

        whenn()
        val created = suspendTransaction { fixture.sut.createInvitation(invitation) }
        val found = suspendTransaction { fixture.sut.getInvitation(fixture.businessId, created.id) }

        then()
        assertEquals(fixture.businessId, created.businessId)
        assertEquals("AAAA1111", created.code)
        assertEquals(EmployeeInvitationStatus.PENDING, created.status)
        assertEquals(invitedBy, found?.invitedBy)
        assertNull(found?.code)
    }

    @Test
    fun `should return invitation by business and id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createInvitation(
                EmployeeInvitation.stub(businessId = fixture.businessId, code = "BBBB2222")
            )
        }

        whenn()
        val found = suspendTransaction { fixture.sut.getInvitation(fixture.businessId, created.id) }

        then()
        assertNotNull(found)
        assertEquals(created.id, found?.id)
        assertNull(found?.code)
        assertEquals(EmployeeInvitationStatus.PENDING, found?.status)
    }

    @Test
    fun `should return null when invitation belongs to another business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId))
        }

        whenn()
        val found = suspendTransaction { fixture.sut.getInvitation(Uuid.random(), created.id) }

        then()
        assertNull(found)
    }

    @Test
    fun `should return invitation by code hash`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createInvitation(
                EmployeeInvitation.stub(businessId = fixture.businessId, code = "CCCC3333")
            )
        }

        whenn()
        val found = suspendTransaction { fixture.sut.getInvitationByCodeHash("CCCC3333") }

        then()
        assertNotNull(found)
        assertEquals(created.id, found?.id)
        assertEquals(fixture.businessId, found?.businessId)
        assertNull(found?.code)
    }

    @Test
    fun `should return null when code hash does not match any invitation`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "DDDD4444"))
        }

        whenn()
        val found = suspendTransaction { fixture.sut.getInvitationByCodeHash("UNKNOWN1") }

        then()
        assertNull(found)
    }

    @Test
    fun `should return invitations matching business and inviter`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val invitedBy = Uuid.random()
        suspendTransaction {
            fixture.sut.createInvitation(
                EmployeeInvitation.stub(businessId = fixture.businessId, invitedBy = invitedBy, code = "AAAA1111")
            )
        }
        suspendTransaction {
            fixture.sut.createInvitation(
                EmployeeInvitation.stub(businessId = fixture.businessId, invitedBy = Uuid.random(), code = "BBBB2222")
            )
        }

        whenn()
        val found = suspendTransaction { fixture.sut.getInvitationsByInviter(fixture.businessId, invitedBy) }

        then()
        assertEquals(1, found.size)
        assertNull(found.first().code)
    }

    @Test
    fun `should return invitations by inviter regardless of status`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val invitedBy = Uuid.random()
        val redeemed = suspendTransaction {
            fixture.sut.createInvitation(
                EmployeeInvitation.stub(businessId = fixture.businessId, invitedBy = invitedBy, code = "AAAA1111")
            )
        }
        suspendTransaction { fixture.sut.redeemInvitation(redeemed.id) }
        suspendTransaction {
            fixture.sut.createInvitation(
                EmployeeInvitation.stub(businessId = fixture.businessId, invitedBy = invitedBy, code = "BBBB2222")
            )
        }

        whenn()
        val found = suspendTransaction { fixture.sut.getInvitationsByInviter(fixture.businessId, invitedBy) }

        then()
        assertEquals(2, found.size)
        assertTrue(found.any { it.code == null && it.status == EmployeeInvitationStatus.REDEEMED })
        assertTrue(found.any { it.code == null && it.status == EmployeeInvitationStatus.PENDING })
    }

    @Test
    fun `should not return invitations from another inviter`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction {
            fixture.sut.createInvitation(
                EmployeeInvitation.stub(businessId = fixture.businessId, invitedBy = Uuid.random())
            )
        }

        whenn()
        val found = suspendTransaction { fixture.sut.getInvitationsByInviter(fixture.businessId, Uuid.random()) }

        then()
        assertTrue(found.isEmpty())
    }

    @Test
    fun `should redeem pending invitation`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId))
        }

        whenn()
        val redeemed = suspendTransaction { fixture.sut.redeemInvitation(created.id) }
        val found = suspendTransaction { fixture.sut.getInvitation(fixture.businessId, created.id) }

        then()
        assertTrue(redeemed)
        assertEquals(EmployeeInvitationStatus.REDEEMED, found?.status)
    }

    @Test
    fun `should not redeem invitation twice`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId))
        }
        suspendTransaction { fixture.sut.redeemInvitation(created.id) }

        whenn()
        val secondRedemption = suspendTransaction { fixture.sut.redeemInvitation(created.id) }

        then()
        assertFalse(secondRedemption)
    }

    @Test
    fun `should return false when redeeming unknown invitation`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val redeemed = suspendTransaction { fixture.sut.redeemInvitation(Uuid.random()) }

        then()
        assertFalse(redeemed)
    }

    @Test
    fun `should revoke pending invitation`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId))
        }

        whenn()
        val revoked = suspendTransaction { fixture.sut.revokeInvitation(created.id) }
        val found = suspendTransaction { fixture.sut.getInvitation(fixture.businessId, created.id) }

        then()
        assertTrue(revoked)
        assertEquals(EmployeeInvitationStatus.REVOKED, found?.status)
    }

    @Test
    fun `should not revoke invitation twice`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId))
        }
        suspendTransaction { fixture.sut.revokeInvitation(created.id) }

        whenn()
        val secondRevocation = suspendTransaction { fixture.sut.revokeInvitation(created.id) }

        then()
        assertFalse(secondRevocation)
    }

    @Test
    fun `should not revoke an already redeemed invitation`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId))
        }
        suspendTransaction { fixture.sut.redeemInvitation(created.id) }

        whenn()
        val revoked = suspendTransaction { fixture.sut.revokeInvitation(created.id) }

        then()
        assertFalse(revoked)
    }

    @Test
    fun `should return false when revoking unknown invitation`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val revoked = suspendTransaction { fixture.sut.revokeInvitation(Uuid.random()) }

        then()
        assertFalse(revoked)
    }

    @Test
    fun `should expire pending invitations created before the cutoff`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId))
        }

        whenn()
        suspendTransaction { fixture.sut.expireOldInvitations(Clock.System.now().plus(1.hours)) }
        val found = suspendTransaction { fixture.sut.getInvitation(fixture.businessId, created.id) }

        then()
        assertEquals(EmployeeInvitationStatus.EXPIRED, found?.status)
    }

    @Test
    fun `should not expire invitations created after the cutoff`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId))
        }

        whenn()
        suspendTransaction { fixture.sut.expireOldInvitations(Instant.fromEpochMilliseconds(0)) }
        val found = suspendTransaction { fixture.sut.getInvitation(fixture.businessId, created.id) }

        then()
        assertEquals(EmployeeInvitationStatus.PENDING, found?.status)
    }

    @Test
    fun `should not expire invitations that are already redeemed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId))
        }
        suspendTransaction { fixture.sut.redeemInvitation(created.id) }

        whenn()
        suspendTransaction { fixture.sut.expireOldInvitations(Clock.System.now().plus(1.hours)) }
        val found = suspendTransaction { fixture.sut.getInvitation(fixture.businessId, created.id) }

        then()
        assertEquals(EmployeeInvitationStatus.REDEEMED, found?.status)
    }

    @Test
    fun `should no longer find a redeemed invitation by its old code hash`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "STALE001"))
        }
        suspendTransaction { fixture.sut.redeemInvitation(created.id) }

        whenn()
        val found = suspendTransaction { fixture.sut.getInvitationByCodeHash("STALE001") }

        then()
        assertNull(found)
    }

    @Test
    fun `should no longer find a revoked invitation by its old code hash`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "STALE002"))
        }
        suspendTransaction { fixture.sut.revokeInvitation(created.id) }

        whenn()
        val found = suspendTransaction { fixture.sut.getInvitationByCodeHash("STALE002") }

        then()
        assertNull(found)
    }

    @Test
    fun `should no longer find an expired invitation by its old code hash`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "STALE003"))
        }
        suspendTransaction { fixture.sut.expireOldInvitations(Clock.System.now().plus(1.hours)) }

        whenn()
        val found = suspendTransaction { fixture.sut.getInvitationByCodeHash("STALE003") }

        then()
        assertNull(found)
    }

    @Test
    fun `should allow reusing a code once its previous invitation is redeemed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val created = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "REUSE001"))
        }
        suspendTransaction { fixture.sut.redeemInvitation(created.id) }

        whenn()
        val recreated = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "REUSE001"))
        }

        then()
        assertEquals("REUSE001", recreated.code)
        assertEquals(EmployeeInvitationStatus.PENDING, recreated.status)
    }

    @Test
    fun `should allow reusing codes from two independently processed invitations`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val first = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "FIRST001"))
        }
        val second = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "SECOND01"))
        }
        suspendTransaction { fixture.sut.redeemInvitation(first.id) }
        suspendTransaction { fixture.sut.revokeInvitation(second.id) }

        whenn()
        val recreatedFirst = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "FIRST001"))
        }
        val recreatedSecond = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "SECOND01"))
        }

        then()
        assertEquals(EmployeeInvitationStatus.PENDING, recreatedFirst.status)
        assertEquals(EmployeeInvitationStatus.PENDING, recreatedSecond.status)
    }

    @Test
    fun `should fail when creating two invitations with the same code`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "SAMECODE"))
        }

        whenn()
        val result = runCatching {
            suspendTransaction {
                fixture.sut.createInvitation(
                    EmployeeInvitation.stub(businessId = fixture.businessId, code = "SAMECODE")
                )
            }
        }

        then()
        assertTrue(result.exceptionOrNull() is Error.UniqueConstraintFailed)
    }

    @Test
    fun `should count only pending invitations of the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "PENDING1"))
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "PENDING2"))
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = otherBusinessId, code = "OTHERBIZ"))
        }
        val redeemed = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "REDEEMED"))
        }
        val revoked = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "REVOKED1"))
        }
        suspendTransaction { fixture.sut.redeemInvitation(redeemed.id) }
        suspendTransaction { fixture.sut.revokeInvitation(revoked.id) }

        whenn()
        val count = suspendTransaction { fixture.sut.countPendingInvitations(fixture.businessId) }

        then()
        assertEquals(2L, count)
    }

    @Test
    fun `should count zero pending invitations for a business without invitations`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val count = suspendTransaction { fixture.sut.countPendingInvitations(fixture.businessId) }

        then()
        assertEquals(0L, count)
    }

    @Test
    fun `should count invitations of the business created since the cutoff regardless of status`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "PENDING1"))
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = otherBusinessId, code = "OTHERBIZ"))
        }
        val revoked = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "REVOKED1"))
        }
        suspendTransaction { fixture.sut.revokeInvitation(revoked.id) }

        whenn()
        val count = suspendTransaction {
            fixture.sut.countInvitationsCreatedSince(fixture.businessId, Instant.fromEpochMilliseconds(0))
        }

        then()
        assertEquals(2L, count)
    }

    @Test
    fun `should not count invitations created before the cutoff`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId))
        }

        whenn()
        val count = suspendTransaction {
            fixture.sut.countInvitationsCreatedSince(fixture.businessId, Clock.System.now().plus(1.hours))
        }

        then()
        assertEquals(0L, count)
    }

    @Test
    fun `should delete processed invitations last updated before the cutoff`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val redeemed = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "REDEEMED"))
        }
        val revoked = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "REVOKED1"))
        }
        val expired = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId, code = "EXPIRED1"))
        }
        suspendTransaction { fixture.sut.redeemInvitation(redeemed.id) }
        suspendTransaction { fixture.sut.revokeInvitation(revoked.id) }
        suspendTransaction { fixture.sut.expireOldInvitations(Clock.System.now().plus(1.hours)) }

        whenn()
        suspendTransaction { fixture.sut.deleteProcessedInvitations(Clock.System.now().plus(1.hours)) }

        then()
        assertNull(suspendTransaction { fixture.sut.getInvitation(fixture.businessId, redeemed.id) })
        assertNull(suspendTransaction { fixture.sut.getInvitation(fixture.businessId, revoked.id) })
        assertNull(suspendTransaction { fixture.sut.getInvitation(fixture.businessId, expired.id) })
    }

    @Test
    fun `should keep processed invitations last updated after the cutoff`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val revoked = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId))
        }
        suspendTransaction { fixture.sut.revokeInvitation(revoked.id) }

        whenn()
        suspendTransaction { fixture.sut.deleteProcessedInvitations(Instant.fromEpochMilliseconds(0)) }

        then()
        assertNotNull(suspendTransaction { fixture.sut.getInvitation(fixture.businessId, revoked.id) })
    }

    @Test
    fun `should never delete pending invitations`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val pending = suspendTransaction {
            fixture.sut.createInvitation(EmployeeInvitation.stub(businessId = fixture.businessId))
        }

        whenn()
        suspendTransaction { fixture.sut.deleteProcessedInvitations(Clock.System.now().plus(1.hours)) }

        then()
        assertEquals(
            EmployeeInvitationStatus.PENDING,
            suspendTransaction { fixture.sut.getInvitation(fixture.businessId, pending.id) }?.status
        )
    }

    @Test
    fun `should delete processed invitations without an update timestamp when created before the cutoff`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val legacyId = suspendTransaction {
            EmployeeInvitationEntity.new {
                businessId = EntityID(fixture.businessId, BusinessTable)
                invitedBy = Uuid.random()
                codeHash = null
                status = EmployeeInvitationStatus.REVOKED
            }.id.value
        }

        whenn()
        suspendTransaction { fixture.sut.deleteProcessedInvitations(Clock.System.now().plus(1.hours)) }

        then()
        assertNull(suspendTransaction { fixture.sut.getInvitation(fixture.businessId, legacyId) })
    }
}
