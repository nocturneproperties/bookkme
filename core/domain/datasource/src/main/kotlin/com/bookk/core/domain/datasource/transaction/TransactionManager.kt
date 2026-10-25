package com.bookk.core.domain.datasource.transaction

interface TransactionManager {
    suspend fun <T> transaction(transaction: suspend () -> T): Result<T>
    suspend fun <T> transaction(isolation: TransactionIsolation, transaction: suspend () -> T): Result<T>
    suspend fun isInTransaction(): Boolean
}

enum class TransactionIsolation {
    READ_COMMITTED,
    REPEATABLE_READ
}
