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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
    }

    @Test
    fun `should find the account linked to a Telegram user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()
        suspendTransaction { fixture.sut.link(account.id, TELEGRAM_USER_ID) }

        whenn()
        val found = suspendTransaction { fixture.sut.getAccountByTelegramUser(TELEGRAM_USER_ID) }

        then()
        assertEquals(account, found)
    }

    @Test
    fun `should find no account for a Telegram user that was never linked`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()
        suspendTransaction { fixture.sut.link(account.id, TELEGRAM_USER_ID) }

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
        suspendTransaction { fixture.sut.link(account.id, TELEGRAM_USER_ID) }

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
        suspendTransaction { fixture.sut.link(first.id, TELEGRAM_USER_ID) }

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.link(second.id, TELEGRAM_USER_ID) } }

        then()
        assertTrue(result.isFailure)
        assertEquals(first, suspendTransaction { fixture.sut.getAccountByTelegramUser(TELEGRAM_USER_ID) })
    }

    @Test
    fun `should forget the Telegram user when the account is deleted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = fixture.account()
        suspendTransaction { fixture.sut.link(account.id, TELEGRAM_USER_ID) }

        whenn()
        suspendTransaction { AuthenticationTable.deleteWhere { AuthenticationTable.id eq account.id } }

        then()
        assertTrue(fixture.storedIndexes().isEmpty())
        assertNull(suspendTransaction { fixture.sut.getAccountByTelegramUser(TELEGRAM_USER_ID) })
    }

    private companion object {
        const val TELEGRAM_USER_ID = 123456789L
    }
}
