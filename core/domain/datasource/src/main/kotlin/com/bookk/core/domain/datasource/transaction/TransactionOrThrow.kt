package com.bookk.core.domain.datasource.transaction

suspend fun <T> TransactionManager.transactionOrThrow(transaction: suspend () -> T): T = transaction(transaction).getOrThrow()

suspend fun <T> TransactionManager.transactionOrThrow(isolation: TransactionIsolation, transaction: suspend () -> T): T = transaction(isolation, transaction).getOrThrow()
