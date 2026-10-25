package library.signing.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import library.signing.GetActiveSigningKey
import library.signing.SigningKey
import library.signing.impl.key.SigningKeyPairGenerator

internal class GetActiveSigningKeyImpl(
    private val signingKeyDataSource: SigningKeyDataSource,
    private val keyPairGenerator: SigningKeyPairGenerator,
    private val transactionManager: TransactionManager
) : GetActiveSigningKey {

    private val mutex = Mutex()

    override suspend fun invoke(): Result<SigningKey> = transactionManager.transaction {
        mutex.withLock {
            signingKeyDataSource.getActiveKey() ?: run {
                val (publicKeyPem, privateKeyPem) = keyPairGenerator.generate()
                signingKeyDataSource.insertKey(publicKeyPem, privateKeyPem)
            }
        }
    }
}
