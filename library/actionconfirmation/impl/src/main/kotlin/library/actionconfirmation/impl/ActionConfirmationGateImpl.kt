package library.actionconfirmation.impl

import com.bookk.core.domain.entity.runCleaningUpOnFailure
import com.bookk.core.domain.entity.runSuspendCatching
import library.actionconfirmation.ActionConfirmationGate
import library.actionconfirmation.ActionConfirmationGrant
import org.slf4j.LoggerFactory

private val actionConfirmationGateLogger = LoggerFactory.getLogger(ActionConfirmationGateImpl::class.java)

internal class ActionConfirmationGateImpl(
    private val verifier: ActionConfirmationVerifier,
    private val tokenStore: ActionConfirmationTokenStore,
) : ActionConfirmationGate {

    override suspend fun <T> spend(token: String, grant: ActionConfirmationGrant, rejection: () -> Throwable, action: suspend () -> T): T {
        val proof = verifier.verify(token, grant) ?: throw rejection()
        if (!tokenStore.reserve(proof.tokenId, proof.remainingLifetime)) throw rejection()
        return runCleaningUpOnFailure(cleanUp = { releaseBestEffort(proof.tokenId) }) { action() }
    }

    private suspend fun releaseBestEffort(tokenId: String) {
        runSuspendCatching { tokenStore.release(tokenId) }
            .onFailure { actionConfirmationGateLogger.error("Failed to release action confirmation token $tokenId: ${it.message}") }
    }
}
