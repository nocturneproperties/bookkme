package com.bookk.core.data

import com.bookk.core.data.map.toDomain
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withTimeout

enum class QueryType(val limit: Long) {
    FAST(10000),
    GENERIC(60000),
    BACKGROUND(5 * 60000)
}

abstract class DataSource {

    suspend fun <T> mapExceptions(
        finally: () -> Unit = {},
        action: suspend () -> T
    ): T {
        return try {
            action()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            throw e.toDomain()
        } finally {
            finally()
        }
    }

    suspend fun <T> dbQuery(
        type: QueryType = QueryType.FAST,
        block: suspend () -> T
    ): T = mapExceptions {
        withTimeout(timeMillis = type.limit) {
            block()
        }
    }

    fun <T> Flow<T>.handleErrors(): Flow<T> {
        return catch { e ->
            throw e.toDomain()
        }
    }
}