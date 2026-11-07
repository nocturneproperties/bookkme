package library.email.impl

import com.bookk.core.data.DataSource
import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.get
import com.bookk.core.data.cache.set
import com.bookk.core.data.cache.setIfAbsent
import kotlin.time.Duration
import kotlin.uuid.Uuid

internal class EmailCodeStore(
    private val cacheClient: CacheClient<String>,
) : DataSource() {

    suspend fun claimCooldown(email: String, cooldown: Duration): Boolean = mapExceptions {
        cacheClient.setIfAbsent(cooldownKey(email), MARKER, cooldown)
    }

    suspend fun releaseCooldown(email: String) = mapExceptions {
        cacheClient.delete(cooldownKey(email))
    }

    suspend fun countSend(email: String, window: Duration): Long = mapExceptions {
        cacheClient.increment(sentKey(email), window)
    }

    suspend fun saveSession(verificationId: Uuid, session: EmailCodeSession, lifetime: Duration) = mapExceptions {
        cacheClient.set(sessionKey(verificationId), session, lifetime)
    }

    suspend fun session(verificationId: Uuid): EmailCodeSession? = mapExceptions {
        cacheClient.get<String, EmailCodeSession>(sessionKey(verificationId))
    }

    suspend fun markLatest(ownerId: Uuid, verificationId: Uuid, lifetime: Duration) = mapExceptions {
        cacheClient.set(latestKey(ownerId), verificationId.toString(), lifetime)
    }

    suspend fun latest(ownerId: Uuid): Uuid? = mapExceptions {
        cacheClient.get<String, String>(latestKey(ownerId))?.let(Uuid::parse)
    }

    suspend fun countAttempt(verificationId: Uuid, lifetime: Duration): Long = mapExceptions {
        cacheClient.increment(attemptsKey(verificationId), lifetime)
    }

    suspend fun claimRedeemed(verificationId: Uuid, lifetime: Duration): Boolean = mapExceptions {
        cacheClient.setIfAbsent(redeemedKey(verificationId), MARKER, lifetime)
    }

    suspend fun releaseRedeemed(verificationId: Uuid) = mapExceptions {
        cacheClient.delete(redeemedKey(verificationId))
    }

    suspend fun discard(verificationId: Uuid) = mapExceptions {
        cacheClient.delete(sessionKey(verificationId))
        cacheClient.delete(attemptsKey(verificationId))
    }

    private companion object {
        const val PREFIX = "email-verification"
        const val MARKER = "1"

        fun sessionKey(verificationId: Uuid) = "$PREFIX:$verificationId"

        fun attemptsKey(verificationId: Uuid) = "$PREFIX:$verificationId:attempts"

        fun redeemedKey(verificationId: Uuid) = "$PREFIX:$verificationId:redeemed"

        fun latestKey(ownerId: Uuid) = "$PREFIX:latest:$ownerId"

        fun cooldownKey(email: String) = "$PREFIX:cooldown:${addressKey(email)}"

        fun sentKey(email: String) = "$PREFIX:sent:${addressKey(email)}"

        fun addressKey(email: String) = sha256Hex(email.lowercase())
    }
}
