package library.signing.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.signing.RotateSigningKeys
import library.signing.SigningKeyStatus
import library.signing.impl.key.SigningKeyPairGenerator
import kotlin.time.Clock
import kotlin.time.Duration

internal class RotateSigningKeysImpl(
    private val signingKeyDataSource: SigningKeyDataSource,
    private val keyPairGenerator: SigningKeyPairGenerator,
    private val transactionManager: TransactionManager
) : RotateSigningKeys {

    override suspend fun invoke(retireInterval: Duration): Result<Unit> = transactionManager.transaction {
        val previousActive = signingKeyDataSource.getActiveKey()
        val (publicKeyPem, privateKeyPem) = keyPairGenerator.generate()
        signingKeyDataSource.insertKey(publicKeyPem, privateKeyPem)

        if (previousActive != null) {
            signingKeyDataSource.updateStatus(previousActive.id, SigningKeyStatus.RETIRING, Clock.System.now())
        }

        signingKeyDataSource.deleteRetiredBefore(Clock.System.now().minus(retireInterval))
    }
}
