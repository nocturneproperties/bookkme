package com.bookk.business.data.datasource

import com.bookk.business.data.orm.table.BusinessDashboardTable
import com.bookk.business.data.orm.table.BusinessDayOffTable
import com.bookk.business.data.orm.table.BusinessPermissionGrantsTable
import com.bookk.business.data.orm.table.BusinessTable
import com.bookk.business.data.orm.table.BusinessWorkingHoursTable
import com.bookk.business.data.orm.table.ClientTable
import com.bookk.business.data.orm.table.EmployeeInvitationTable
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.employee.entity.EmployeeInvitation
import com.bookk.business.domain.api.export.BusinessClientExport
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class BusinessDataExportDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(
            BusinessTable,
            BusinessDashboardTable,
            BusinessPermissionGrantsTable,
            BusinessWorkingHoursTable,
            BusinessDayOffTable,
            ClientTable,
            EmployeeInvitationTable,
        )
        val contactCipher = testContactCipher()
        val sut = BusinessDataExportDataSourceImpl(contactCipher)
        val businessSut = BusinessDataSourceImpl()
        val clientSut = ClientDataSourceImpl(contactCipher)
        val invitationSut = EmployeeInvitationDataSourceImpl()

        suspend fun business(): Uuid = suspendTransaction {
            businessSut.createBusiness(Uuid.random(), "Studio", "UAH", TimeZone.UTC)
        }.id
    }

    @Test
    fun `should export every client of the business with its account link and last activity`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.business()
        val otherBusinessId = fixture.business()
        val detached = suspendTransaction {
            fixture.clientSut.createDetachedClient(
                businessId,
                Client.Detached.stub(
                    name = "Ivan",
                    lastName = "Petrenko",
                    phone = "+380509998877",
                    email = "ivan@example.com",
                    description = "prefers mornings",
                ),
            )
        }
        val integrated = suspendTransaction {
            fixture.clientSut.getOrCreateIntegratedClient(
                businessId,
                Client.Integrated.stub(
                    name = "Olena",
                    lastName = "Koval",
                    phone = "+380501112233",
                    email = "olena@example.com",
                ),
            )
        }
        suspendTransaction {
            fixture.clientSut.createDetachedClient(
                otherBusinessId,
                Client.Detached.stub(phone = "+380500000000", email = "other@example.com"),
            )
        }
        val lastActivityAt = Instant.fromEpochMilliseconds(1_700_000_000_000)
        suspendTransaction {
            ClientTable.update({ ClientTable.businessId eq businessId }) {
                it[this.lastActivityAt] =
                    lastActivityAt
            }
        }

        whenn()
        val clients = suspendTransaction { fixture.sut.getClients(businessId) }

        then()
        assertEquals(
            setOf(
                BusinessClientExport(
                    detached.id,
                    "Ivan",
                    "Petrenko",
                    "+380509998877",
                    "ivan@example.com",
                    "prefers mornings",
                    hasAccount = false,
                    lastActivityAt = lastActivityAt,
                ),
                BusinessClientExport(
                    integrated.id,
                    "Olena",
                    "Koval",
                    "+380501112233",
                    "olena@example.com",
                    null,
                    hasAccount = true,
                    lastActivityAt = lastActivityAt,
                ),
            ),
            clients.toSet(),
        )
    }

    @Test
    fun `should export only the business's pending invitations`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = fixture.business()
        val inviter = Uuid.random()
        val pending =
            suspendTransaction {
                fixture.invitationSut.createInvitation(
                    EmployeeInvitation.stub(businessId = businessId, invitedBy = inviter, code = "PENDING1"),
                )
            }
        val revoked =
            suspendTransaction {
                fixture.invitationSut.createInvitation(
                    EmployeeInvitation.stub(businessId = businessId, invitedBy = inviter, code = "REVOKED1"),
                )
            }
        suspendTransaction { fixture.invitationSut.revokeInvitation(revoked.id) }
        suspendTransaction {
            fixture.invitationSut.createInvitation(
                EmployeeInvitation.stub(businessId = fixture.business(), code = "ELSEWHER"),
            )
        }

        whenn()
        val invitations = suspendTransaction { fixture.sut.getPendingInvitations(businessId) }

        then()
        assertEquals(listOf(pending.id to inviter), invitations.map { it.id to it.invitedBy })
    }
}
