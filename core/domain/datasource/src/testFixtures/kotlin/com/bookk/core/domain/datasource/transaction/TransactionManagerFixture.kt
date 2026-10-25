package com.bookk.core.domain.datasource.transaction

import io.mockk.coEvery
import kotlin.coroutines.cancellation.CancellationException

fun TransactionManager.mockTransaction() {
    coEvery { transaction<Any>(any()) } coAnswers { runMocked(firstArg()) }
    coEvery { transaction<Any>(any<TransactionIsolation>(), any()) } coAnswers { runMocked(secondArg()) }
}

private suspend fun runMocked(transaction: suspend () -> Any): Result<Any> =
    try {
        Result.success(transaction())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
