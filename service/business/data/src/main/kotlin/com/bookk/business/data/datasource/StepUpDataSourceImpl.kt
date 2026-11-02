package com.bookk.business.data.datasource

import com.bookk.business.domain.datasource.StepUpDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.setIfAbsent
import kotlin.time.Duration

internal class StepUpDataSourceImpl(
    private val cacheClient: CacheClient<String>,
) : DataSource(), StepUpDataSource {

    override suspend fun reserve(tokenId: String, lifetime: Duration): Boolean = mapExceptions {
        cacheClient.setIfAbsent(key(tokenId), USED_MARKER, lifetime)
    }

    override suspend fun release(tokenId: String) = mapExceptions {
        cacheClient.delete(key(tokenId))
    }

    private fun key(tokenId: String) = "step_up:$tokenId"

    private companion object {
        const val USED_MARKER = "used"
    }
}
