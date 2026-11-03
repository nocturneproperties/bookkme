package library.stepup.impl

import com.bookk.core.domain.entity.runCleaningUpOnFailure
import com.bookk.core.domain.entity.runSuspendCatching
import library.stepup.StepUpGate
import library.stepup.StepUpGrant
import org.slf4j.LoggerFactory

private val stepUpGateLogger = LoggerFactory.getLogger(StepUpGateImpl::class.java)

internal class StepUpGateImpl(
    private val verifier: StepUpVerifier,
    private val tokenStore: StepUpTokenStore,
) : StepUpGate {

    override suspend fun <T> spend(token: String, grant: StepUpGrant, rejection: () -> Throwable, action: suspend () -> T): T {
        val proof = verifier.verify(token, grant) ?: throw rejection()
        if (!tokenStore.reserve(proof.tokenId, proof.remainingLifetime)) throw rejection()
        return runCleaningUpOnFailure(cleanUp = { releaseBestEffort(proof.tokenId) }) { action() }
    }

    private suspend fun releaseBestEffort(tokenId: String) {
        runSuspendCatching { tokenStore.release(tokenId) }
            .onFailure { stepUpGateLogger.error("Failed to release step-up token $tokenId: ${it.message}") }
    }
}
