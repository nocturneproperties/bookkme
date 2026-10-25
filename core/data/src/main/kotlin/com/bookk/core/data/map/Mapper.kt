package com.bookk.core.data.map

import com.bookk.core.domain.entity.Error
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import java.sql.SQLException
import java.sql.SQLIntegrityConstraintViolationException

private const val INTEGRITY_CONSTRAINT_VIOLATION_STATE_CLASS = "23"
private const val SERIALIZATION_FAILURE_STATE = "40001"
private const val RECORD_CHANGED_SINCE_LAST_READ_ERROR_CODE = 1020
private const val DEADLOCK_ERROR_CODE = 1213

fun Throwable.toDomain(): Error {
    return when (this) {
        is Error -> this
        is ExposedSQLException -> when {
            isTransactionConflict() -> Error.TransactionConflict(message.orEmpty(), this)
            (cause as? SQLException)?.isIntegrityConstraintViolation() == true ->
                Error.UniqueConstraintFailed(message.orEmpty(), this)
            else -> Error.DatabaseError(message.orEmpty(), this)
        }
        is SQLException -> when {
            isTransactionConflict() -> Error.TransactionConflict(message.orEmpty(), this)
            else -> Error.DatabaseError(message.orEmpty(), this)
        }

        else -> Error.UnknownError( this)
    }
}

private fun SQLException.isIntegrityConstraintViolation(): Boolean =
    this is SQLIntegrityConstraintViolationException ||
        sqlState?.startsWith(INTEGRITY_CONSTRAINT_VIOLATION_STATE_CLASS) == true

private fun SQLException.isTransactionConflict(): Boolean =
    generateSequence<Throwable>(this) { it.cause }
        .filterIsInstance<SQLException>()
        .any {
            it.errorCode == RECORD_CHANGED_SINCE_LAST_READ_ERROR_CODE ||
                it.errorCode == DEADLOCK_ERROR_CODE ||
                it.sqlState == SERIALIZATION_FAILURE_STATE
        }
