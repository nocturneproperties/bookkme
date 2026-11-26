package com.bookk.auth.data.datasource

import com.bookk.auth.data.orm.table.DemoSettingsTable
import com.bookk.auth.domain.api.demo.entity.DemoAccountPolicy
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DemoSettingsDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(DemoSettingsTable)
        val sut = DemoSettingsDataSourceImpl()

        suspend fun storeSettings(maxActiveAccounts: Int?) = suspendTransaction {
            DemoSettingsTable.insert {
                it[id] = DemoSettingsTable.ROW_ID
                it[this.maxActiveAccounts] = maxActiveAccounts
            }
        }
    }

    @Test
    fun `should return the default cap when no settings row exists`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val cap = suspendTransaction { fixture.sut.getMaxActiveAccounts() }

        then()
        assertEquals(DemoAccountPolicy.DEFAULT_MAX_ACTIVE_ACCOUNTS, cap)
    }

    @Test
    fun `should return the default cap while the stored cap is null`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.storeSettings(maxActiveAccounts = null)

        whenn()
        val cap = suspendTransaction { fixture.sut.getMaxActiveAccounts() }

        then()
        assertEquals(DemoAccountPolicy.DEFAULT_MAX_ACTIVE_ACCOUNTS, cap)
    }

    @Test
    fun `should return the stored cap`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.storeSettings(maxActiveAccounts = 250)

        whenn()
        val cap = suspendTransaction { fixture.sut.getMaxActiveAccounts() }

        then()
        assertEquals(250, cap)
    }

    @Test
    fun `should return a stored zero rather than the default`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.storeSettings(maxActiveAccounts = 0)

        whenn()
        val cap = suspendTransaction { fixture.sut.getMaxActiveAccounts() }

        then()
        assertEquals(0, cap)
    }
}
