package com.bookk.auth.data.datasource

import com.bookk.auth.data.orm.table.AuthenticationTable
import com.bookk.auth.data.orm.table.TelegramIdentityTable
import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.encryption.test.UnkeyedBlindIndex
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class TelegramIdentityDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(AuthenticationTable, TelegramIdentityTable)
        val accounts = AccountDataSourceImpl()
        val sut = TelegramIdentityDataSourceImpl(UnkeyedBlindIndex())

        suspend fun account(): Authentication = suspendTransaction {
            accounts.createAuthorization(Authentication.stub())
        }

        suspend fun storedIndexes(): List<String> = suspendTransaction {
            TelegramIdentityTable.selectAll().map { it[TelegramIdentityTable.telegramIdIndex] }
        }

        suspend fun storedUsernameIndexes(): List<String?> = suspendTransaction {
            TelegramIdentityTable.selectAll().map { it[TelegramIdentityTable.usernameIndex] }
        }

        suspend fun forgetSyncedUsername(authId: Uuid) = suspendTransaction {
            TelegramIdentityTable.update(where = { TelegramIdentityTable.authId eq authId }) {
                it[usernameIndex] = null
            }
        }
    }

    @Test
    fun `should find the account linked to a Telegram user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()
        suspendTransaction { fixture.sut.link(account.id, TELEGRAM_USER_ID, USERNAME) }

        whenn()
        val found = suspendTransaction { fixture.sut.getAccountByTelegramUser(TELEGRAM_USER_ID) }

        then()
        assertEquals(account, found)
    }

    @Test
    fun `should tell that a user has a Telegram identity`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()
        suspendTransaction { fixture.sut.link(account.id, TELEGRAM_USER_ID, USERNAME) }

        whenn()
        val linked = suspendTransaction { fixture.sut.hasIdentity(account.userId) }

        then()
        assertTrue(linked)
    }

    @Test
    fun `should tell that a user without a Telegram link has no Telegram identity`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val linkedAccount = fixture.account()
        val passkeyAccount = fixture.account()
        suspendTransaction { fixture.sut.link(linkedAccount.id, TELEGRAM_USER_ID, USERNAME) }

        whenn()
        val linked = suspendTransaction { fixture.sut.hasIdentity(passkeyAccount.userId) }

        then()
        assertFalse(linked)
    }

    @Test
    fun `should find no account for a Telegram user that was never linked`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()
        suspendTransaction { fixture.sut.link(account.id, TELEGRAM_USER_ID, USERNAME) }

        whenn()
        val found = suspendTransaction { fixture.sut.getAccountByTelegramUser(TELEGRAM_USER_ID + 1) }

        then()
        assertNull(found)
    }

    @Test
    fun `should store only the blind index of the Telegram user id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()

        whenn()
        suspendTransaction { fixture.sut.link(account.id, TELEGRAM_USER_ID, USERNAME) }

        then()
        val indexes = fixture.storedIndexes()
        assertEquals(listOf(UnkeyedBlindIndex().of(TELEGRAM_USER_ID.toString())), indexes)
        assertFalse(indexes.single().contains(TELEGRAM_USER_ID.toString()))
    }

    @Test
    fun `should refuse to link one Telegram user to a second account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = fixture.account()
        val second = fixture.account()
        suspendTransaction { fixture.sut.link(first.id, TELEGRAM_USER_ID, USERNAME) }

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.link(second.id, TELEGRAM_USER_ID, USERNAME) } }

        then()
        assertTrue(result.isFailure)
        assertEquals(first, suspendTransaction { fixture.sut.getAccountByTelegramUser(TELEGRAM_USER_ID) })
    }

    @Test
    fun `should forget the Telegram user when the account is deleted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()
        suspendTransaction { fixture.sut.link(account.id, TELEGRAM_USER_ID, USERNAME) }

        whenn()
        suspendTransaction { AuthenticationTable.deleteWhere { AuthenticationTable.id eq account.id } }

        then()
        assertTrue(fixture.storedIndexes().isEmpty())
        assertNull(suspendTransaction { fixture.sut.getAccountByTelegramUser(TELEGRAM_USER_ID) })
    }

    @Test
    fun `should remember the username the account was linked with`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()
        suspendTransaction { fixture.sut.link(account.id, TELEGRAM_USER_ID, USERNAME) }

        whenn()
        val same = suspendTransaction { fixture.sut.isUsernameSynced(account.id, USERNAME) }
        val renamed = suspendTransaction { fixture.sut.isUsernameSynced(account.id, "ann_renamed") }
        val removed = suspendTransaction { fixture.sut.isUsernameSynced(account.id, null) }

        then()
        assertTrue(same)
        assertFalse(renamed)
        assertFalse(removed)
    }

    @Test
    fun `should tell an account linked without a username from one never synced`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val withoutUsername = fixture.account()
        val neverSynced = fixture.account()
        suspendTransaction {
            fixture.sut.link(withoutUsername.id, TELEGRAM_USER_ID, null)
            fixture.sut.link(neverSynced.id, TELEGRAM_USER_ID + 1, null)
        }
        fixture.forgetSyncedUsername(neverSynced.id)

        whenn()
        val withoutUsernameSynced = suspendTransaction { fixture.sut.isUsernameSynced(withoutUsername.id, null) }
        val neverSyncedSynced = suspendTransaction { fixture.sut.isUsernameSynced(neverSynced.id, null) }

        then()
        assertTrue(withoutUsernameSynced)
        assertFalse(neverSyncedSynced)
    }

    @Test
    fun `should remember the last synced username`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()
        suspendTransaction { fixture.sut.link(account.id, TELEGRAM_USER_ID, USERNAME) }

        whenn()
        suspendTransaction { fixture.sut.markUsernameSynced(account.id, "ann_renamed") }

        then()
        assertTrue(suspendTransaction { fixture.sut.isUsernameSynced(account.id, "ann_renamed") })
        assertFalse(suspendTransaction { fixture.sut.isUsernameSynced(account.id, USERNAME) })
    }

    @Test
    fun `should store only the blind index of the synced username`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()

        whenn()
        suspendTransaction { fixture.sut.link(account.id, TELEGRAM_USER_ID, USERNAME) }

        then()
        assertEquals(listOf(UnkeyedBlindIndex().of(USERNAME)), fixture.storedUsernameIndexes())
    }

    @Test
    fun `should report a username as not synced for an account without a Telegram link`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()

        whenn()
        val synced = suspendTransaction { fixture.sut.isUsernameSynced(account.id, null) }

        then()
        assertFalse(synced)
    }

    private companion object {
        const val TELEGRAM_USER_ID = 123456789L
        const val USERNAME = "ann_lee"
    }
}
