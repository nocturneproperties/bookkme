package com.bookk.core.data

import com.bookk.core.data.map.toDomain
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.SQLException

internal class MapperTest {

    @Test
    fun `should map a record changed since the snapshot to a transaction conflict`() = runUnitTest {
        given()
        val exception = SQLException("Record has changed since last read in table 'service'", "HY000", 1020)

        whenn()
        val mapped = exception.toDomain()

        then()
        assertTrue(mapped is Error.TransactionConflict)
    }

    @Test
    fun `should map a deadlock to a transaction conflict`() = runUnitTest {
        given()
        val exception = SQLException("Deadlock found when trying to get lock", "40001", 1213)

        whenn()
        val mapped = exception.toDomain()

        then()
        assertTrue(mapped is Error.TransactionConflict)
    }

    @Test
    fun `should map a conflict reported by a chained cause to a transaction conflict`() = runUnitTest {
        given()
        val exception = SQLException("batch failed", "HY000", 0, SQLException("Deadlock found when trying to get lock", "40001", 1213))

        whenn()
        val mapped = exception.toDomain()

        then()
        assertTrue(mapped is Error.TransactionConflict)
    }

    @Test
    fun `should keep mapping an unrelated database error to a database error`() = runUnitTest {
        given()
        val exception = SQLException("Lock wait timeout exceeded", "HY000", 1205)

        whenn()
        val mapped = exception.toDomain()

        then()
        assertTrue(mapped is Error.DatabaseError)
    }
}
