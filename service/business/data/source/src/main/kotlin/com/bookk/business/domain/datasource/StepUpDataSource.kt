package com.bookk.business.domain.datasource

import kotlin.time.Duration

interface StepUpDataSource {
    suspend fun reserve(tokenId: String, lifetime: Duration): Boolean
    suspend fun release(tokenId: String)
}
