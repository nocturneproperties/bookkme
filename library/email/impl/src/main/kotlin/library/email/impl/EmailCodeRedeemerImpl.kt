package library.email.impl

import com.bookk.core.domain.entity.runCleaningUpOnFailure
import com.bookk.core.domain.entity.runSuspendCatching
import library.email.EmailCodeError
import library.email.EmailCodeRedeemer
import library.email.VerifiedEmail
import library.email.impl.EmailCodeIssuerImpl.Companion.CODE_LIFETIME
import kotlin.uuid.Uuid

internal class EmailCodeRedeemerImpl(
    private val store: EmailCodeStore,
    private val hasher: EmailCodeHasher,
) : EmailCodeRedeemer {

    override suspend fun <T> redeem(verificationId: Uuid, code: String, ownerId: Uuid, action: suspend (VerifiedEmail) -> T): Result<T> = runSuspendCatching {
        val session = currentSession(verificationId, ownerId) ?: throw EmailCodeError.Expired()
        if (store.countAttempt(verificationId, CODE_LIFETIME) > MAX_ATTEMPTS) {
            store.discard(verificationId)
            throw EmailCodeError.AttemptsExceeded()
        }
        if (!hasher.matches(session.codeHash, verificationId, code)) throw EmailCodeError.Invalid()
        if (!store.claimRedeemed(verificationId, CODE_LIFETIME)) throw EmailCodeError.Expired()
        runCleaningUpOnFailure(cleanUp = { store.releaseRedeemed(verificationId) }) {
            action(VerifiedEmail(email = session.email, purpose = session.purpose))
        }
    }

    private suspend fun currentSession(verificationId: Uuid, ownerId: Uuid): EmailCodeSession? {
        val session = store.session(verificationId) ?: return null
        val belongsToCaller = session.ownerId == ownerId
        return session.takeIf { belongsToCaller && store.latest(ownerId) == verificationId }
    }

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}
