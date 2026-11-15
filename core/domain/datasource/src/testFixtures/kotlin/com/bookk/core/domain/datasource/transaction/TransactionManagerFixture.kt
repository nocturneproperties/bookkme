package com.bookk.core.domain.datasource.transaction

import io.mockk.coEvery
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

fun TransactionManager.mockTransaction(onRollback: () -> Unit = {}) {
    val openTransactions = AtomicInteger(0)
    coEvery { isInTransaction() } coAnswers { openTransactions.get() > 0 }
    coEvery { transaction<Any>(any()) } coAnswers { runMocked(openTransactions, onRollback, firstArg()) }
    coEvery { transaction<Any>(any<TransactionIsolation>(), any()) } coAnswers
        { runMocked(openTransactions, onRollback, secondArg()) }
}

private suspend fun runMocked(
    openTransactions: AtomicInteger,
    onRollback: () -> Unit,
    transaction: suspend () -> Any,
): Result<Any> {
    openTransactions.incrementAndGet()
    return try {
        Result.success(transaction())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        onRollback()
        Result.failure(e)
    } finally {
        openTransactions.decrementAndGet()
    }
}

fun mockedTransactionManager(): TransactionManager = mockk<TransactionManager>().apply { mockTransaction() }
