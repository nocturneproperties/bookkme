package com.bookk.auth.domain.impl.operation.identification

import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.auth.domain.api.identification.entity.PasskeyList
import com.bookk.auth.domain.api.identification.entity.PasskeyResponse
import com.bookk.auth.domain.api.identification.operation.GetAvailablePasskeys
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.PassKeyDataSource
import com.bookk.core.AppLevelConstants
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import kotlin.uuid.Uuid
import com.yubico.webauthn.data.ByteArray as YubicoByteArray

internal class GetAvailablePasskeysImpl(
    private val passKeyDataSource: PassKeyDataSource,
    private val accountDataSource: AccountDataSource,
    private val transactionManager: TransactionManager,
) : GetAvailablePasskeys {
    override suspend fun invoke(authId: Uuid): Result<PasskeyList> = transactionManager.transaction {
        val account = accountDataSource.getAuthRecordById(authId) ?: throw Error.NotFound()
        val passkeys = passKeyDataSource.getCredentialBy(authId).map { it.asResponse() }
        PasskeyList(
            rpId = AppLevelConstants.domainName,
            userHandle = YubicoByteArray(account.uuid.toByteArray()).base64Url,
            passkeys = passkeys,
        )
    }

    private fun PasskeyCredential.asResponse(): PasskeyResponse {
        return PasskeyResponse(
            id = id,
            name = name,
            createdAt = createdAt,
            lastUsedAt = lastUsedAt,
            isBackedUp = isBackedUp,
            credentialId = YubicoByteArray(credDescriptor.id).base64Url,
        )
    }
}
