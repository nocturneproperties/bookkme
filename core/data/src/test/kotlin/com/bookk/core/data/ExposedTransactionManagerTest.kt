package com.bookk.core.data

import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException
import kotlin.coroutines.cancellation.CancellationException
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager as JdbcTransactionManager

internal class ExposedTransactionManagerTest {

    private class SutFixture {
        val sut = ExposedTransactionManager(createTestDatabase())
    }

    @Test
    fun `should return success holding the transaction value`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut.transaction { "value" }

        then()
        assertEquals("value", result.getOrNull())
    }

    @Test
    fun `should run the transaction at the requested isolation level`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut.transaction(TransactionIsolation.READ_COMMITTED) {
            JdbcTransactionManager.current().connection.transactionIsolation
        }

        then()
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, result.getOrNull())
    }

    @Test
    fun `should map an unexpected exception to an unknown error`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut.transaction { throw IllegalStateException("failure") }

        then()
        assertTrue(result.exceptionOrNull() is Error.UnknownError)
    }

    @Test
    fun `should rethrow cancellation exception instead of returning failure`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val thrown = runCatching { fixture.sut.transaction { throw CancellationException("cancelled") } }.exceptionOrNull()

        then()
        assertTrue(thrown is CancellationException)
        assertEquals("cancelled", thrown?.message)
    }

    @Test
    fun `should retry a conflicting transaction and return the result of the attempt that succeeds`() = runUnitTest {
        given()
        val fixture = SutFixture()
        var attempts = 0

        whenn()
        val result = fixture.sut.transaction {
            attempts++
            if (attempts == 1) throw Error.TransactionConflict("conflict", SQLException())
            "value"
        }

        then()
        assertEquals("value", result.getOrNull())
        assertEquals(2, attempts)
    }

    @Test
    fun `should give up with a transaction conflict once every attempt conflicted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        var attempts = 0

        whenn()
        val result = fixture.sut.transaction<Unit> {
            attempts++
            throw Error.TransactionConflict("conflict", SQLException())
        }

        then()
        assertTrue(result.exceptionOrNull() is Error.TransactionConflict)
        assertEquals(ExposedTransactionManager.MAX_ATTEMPTS_ON_CONFLICT, attempts)
    }

    @Test
    fun `should not retry a failure that is not a transaction conflict`() = runUnitTest {
        given()
        val fixture = SutFixture()
        var attempts = 0

        whenn()
        val result = fixture.sut.transaction<Unit> {
            attempts++
            throw Error.NotFound()
        }

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        assertEquals(1, attempts)
    }

    @Test
    fun `should run a block failing with a raw sql exception exactly once`() = runUnitTest {
        given()
        val fixture = SutFixture()
        var attempts = 0

        whenn()
        val result = fixture.sut.transaction<Unit> {
            attempts++
            throw SQLException("broken")
        }

        then()
        assertTrue(result.exceptionOrNull() is Error.DatabaseError)
        assertEquals(1, attempts)
    }

    @Test
    fun `should retry only the outermost transaction when a nested one conflicts`() = runUnitTest {
        given()
        val fixture = SutFixture()
        var outerAttempts = 0
        var innerAttempts = 0

        whenn()
        val result = fixture.sut.transaction {
            outerAttempts++
            fixture.sut.transaction {
                innerAttempts++
                if (innerAttempts == 1) throw Error.TransactionConflict("conflict", SQLException())
                "value"
            }.getOrThrow()
        }

        then()
        assertEquals("value", result.getOrNull())
        assertEquals(2, outerAttempts)
        assertEquals(2, innerAttempts)
    }

    @Test
    fun `should report being in a transaction inside its own transaction block`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut.transaction { fixture.sut.isInTransaction() }

        then()
        assertEquals(true, result.getOrNull())
    }

    @Test
    fun `should report not being in a transaction outside any transaction block`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val inTransaction = fixture.sut.isInTransaction()

        then()
        assertFalse(inTransaction)
    }

    @Test
    fun `should report not being in a transaction inside a transaction of another database`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val otherDatabase = ExposedTransactionManager(createTestDatabase())

        whenn()
        val result = otherDatabase.transaction { fixture.sut.isInTransaction() }

        then()
        assertEquals(false, result.getOrNull())
    }
}
