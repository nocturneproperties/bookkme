package com.bookk.auth.data.datasource

import com.bookk.auth.data.orm.table.AuthenticationTable
import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.authentication.entity.DormancyStage
import com.bookk.auth.domain.api.authentication.entity.DormantAccount
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AccountDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(AuthenticationTable)
        val sut = AccountDataSourceImpl()

        suspend fun backdate(authId: Uuid, lastActiveAt: Instant) = suspendTransaction {
            AuthenticationTable.update(where = { AuthenticationTable.id eq authId }) { it[AuthenticationTable.lastActiveAt] = lastActiveAt }
        }
    }

    @Test
    fun `should create authorization and retrieve by id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = Authentication.stub()

        whenn()
        val created = suspendTransaction { fixture.sut.createAuthorization(auth) }
        val found = suspendTransaction { fixture.sut.getAuthRecordById(created.id) }

        then()
        assertNotNull(found)
        assertEquals(auth.userId, found!!.userId)
        assertEquals(auth.uuid, found.uuid)
    }

    @Test
    fun `should return null when auth record not found by id`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getAuthRecordById(Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should retrieve auth record by uuid`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = Authentication.stub()
        suspendTransaction { fixture.sut.createAuthorization(auth) }

        whenn()
        val found = suspendTransaction { fixture.sut.getAuthRecordByUUID(auth.uuid) }

        then()
        assertNotNull(found)
        assertEquals(auth.userId, found!!.userId)
    }

    @Test
    fun `should return null when auth record not found by uuid`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getAuthRecordByUUID(Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should retrieve auth record by userId`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = Authentication.stub()
        suspendTransaction { fixture.sut.createAuthorization(auth) }

        whenn()
        val found = suspendTransaction { fixture.sut.getAuthRecordByUserId(auth.userId) }

        then()
        assertNotNull(found)
        assertEquals(auth.uuid, found!!.uuid)
    }

    @Test
    fun `should return null when auth record not found by userId`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getAuthRecordByUserId(Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should delete authorization`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = Authentication.stub()
        val created = suspendTransaction { fixture.sut.createAuthorization(auth) }

        whenn()
        suspendTransaction { fixture.sut.deleteAuthorization(created.id) }
        val found = suspendTransaction { fixture.sut.getAuthRecordById(created.id) }

        then()
        assertNull(found)
    }

    @Test
    fun `should count a new account as active from the moment it is created`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val before = Clock.System.now()

        whenn()
        val created = suspendTransaction { fixture.sut.createAuthorization(Authentication.stub()) }

        then()
        val activity = suspendTransaction { fixture.sut.getLastActiveAt(listOf(created.userId)) }
        assertTrue(activity.getValue(created.userId) >= before)
    }

    @Test
    fun `should record activity and clear any dormancy warning`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.createAuthorization(Authentication.stub()) }
        fixture.backdate(created.id, Instant.fromEpochMilliseconds(0))
        suspendTransaction { fixture.sut.setDormancyStage(created.userId, DormancyStage.SENT_60_DAY_WARNING, Instant.fromEpochMilliseconds(1000)) }
        val now = Clock.System.now()

        whenn()
        suspendTransaction { fixture.sut.recordActivity(created.id, at = now, unlessRecordedSince = now - 1.days) }

        then()
        assertEquals(now, suspendTransaction { fixture.sut.getLastActiveAt(listOf(created.userId)) }[created.userId])
        assertNull(suspendTransaction { fixture.sut.getDormantAccounts(inactiveSince = now + 1.days) }.single().warnedAt)
        assertEquals(DormancyStage.ACTIVE, suspendTransaction { fixture.sut.getDormantAccountForUpdate(created.userId) }?.stage)
    }

    @Test
    fun `should not rewrite activity recorded recently`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.createAuthorization(Authentication.stub()) }
        val recorded = Clock.System.now() - 1.hours
        fixture.backdate(created.id, recorded)

        whenn()
        val now = Clock.System.now()
        suspendTransaction { fixture.sut.recordActivity(created.id, at = now, unlessRecordedSince = now - 1.days) }

        then()
        assertEquals(recorded, suspendTransaction { fixture.sut.getLastActiveAt(listOf(created.userId)) }[created.userId])
    }

    @Test
    fun `should list only the accounts inactive since the cutoff`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val dormant = suspendTransaction { fixture.sut.createAuthorization(Authentication.stub()) }
        val active = suspendTransaction { fixture.sut.createAuthorization(Authentication.stub()) }
        fixture.backdate(dormant.id, Instant.fromEpochMilliseconds(0))

        whenn()
        val found = suspendTransaction { fixture.sut.getDormantAccounts(inactiveSince = Clock.System.now() - 1.days) }

        then()
        assertEquals(listOf(DormantAccount(dormant.id, dormant.userId, Instant.fromEpochMilliseconds(0), DormancyStage.ACTIVE, null)), found)
        assertTrue(found.none { it.userId == active.userId })
    }

    @Test
    fun `should store the dormancy stage and when the first warning went out`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.createAuthorization(Authentication.stub()) }
        val warnedAt = Instant.fromEpochMilliseconds(5000)

        whenn()
        suspendTransaction { fixture.sut.setDormancyStage(created.userId, DormancyStage.SENT_7_DAY_WARNING, warnedAt) }

        then()
        val account = suspendTransaction { fixture.sut.getDormantAccountForUpdate(created.userId) }
        assertEquals(DormancyStage.SENT_7_DAY_WARNING, account?.stage)
        assertEquals(warnedAt, account?.warnedAt)
    }

    @Test
    fun `should report no dormancy for an unknown user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val account = suspendTransaction { fixture.sut.getDormantAccountForUpdate(Uuid.random()) }

        then()
        assertNull(account)
    }

    @Test
    fun `should report the last activity of only the requested users`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = suspendTransaction { fixture.sut.createAuthorization(Authentication.stub()) }
        val other = suspendTransaction { fixture.sut.createAuthorization(Authentication.stub()) }

        whenn()
        val activity = suspendTransaction { fixture.sut.getLastActiveAt(listOf(first.userId, Uuid.random())) }

        then()
        assertEquals(setOf(first.userId), activity.keys)
        assertTrue(other.userId !in activity)
    }
}
