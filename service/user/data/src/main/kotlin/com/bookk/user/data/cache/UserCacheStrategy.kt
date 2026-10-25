package com.bookk.user.data.cache

import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.get
import com.bookk.core.data.cache.set
import com.bookk.user.domain.api.entity.User
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

object UserCacheStrategy {
    private val cacheExpiration = 10.minutes

    suspend fun CacheClient<String>.save(user: User) {
        set(cacheKeyFrom(user.id), user, cacheExpiration)
    }

    suspend fun CacheClient<String>.getUser(id: Uuid): User? {
        return get(cacheKeyFrom(id))
    }

    suspend fun CacheClient<String>.deleteUser(id: Uuid) {
        delete(cacheKeyFrom(id))
    }

    private fun cacheKeyFrom(id: Uuid): String {
        return "user_$id"
    }
}