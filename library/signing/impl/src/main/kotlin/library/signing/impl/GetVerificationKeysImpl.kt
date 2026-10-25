package library.signing.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.signing.GetVerificationKeys
import library.signing.SigningKey

internal class GetVerificationKeysImpl(
    private val signingKeyDataSource: SigningKeyDataSource,
    private val transactionManager: TransactionManager
) : GetVerificationKeys {

    override suspend fun invoke(): Result<List<SigningKey>> = transactionManager.transaction {
        signingKeyDataSource.getVerificationKeys()
    }
}
