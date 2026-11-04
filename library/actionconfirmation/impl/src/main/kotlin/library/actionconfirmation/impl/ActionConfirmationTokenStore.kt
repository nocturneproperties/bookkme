package library.actionconfirmation.impl

import com.bookk.core.data.DataSource
import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.setIfAbsent
import kotlin.time.Duration

internal class ActionConfirmationTokenStore(
    private val cacheClient: CacheClient<String>,
) : DataSource() {

    suspend fun reserve(tokenId: String, lifetime: Duration): Boolean = mapExceptions {
        cacheClient.setIfAbsent(key(tokenId), USED_MARKER, lifetime)
    }

    suspend fun release(tokenId: String) = mapExceptions {
        cacheClient.delete(key(tokenId))
    }

    companion object {
        private const val USED_MARKER = "used"

        fun key(tokenId: String) = "action_confirmation:$tokenId"
    }
}
