package com.bookk.auth.domain.datasource

import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import kotlin.uuid.Uuid

interface PassKeyDataSource {
    suspend fun saveChallengeToCache(requestId: String, challenge: String)
    suspend fun getCachedChallenge(requestId: String): String?
    suspend fun deleteCachedChallenge(requestId: String)
    suspend fun createPasskeyCredential(credential: PasskeyCredential)
    suspend fun getCredentialBy(userHandle: Uuid, credentialId: ByteArray): PasskeyCredential?
    suspend fun getCredentialBy(authId: Uuid): List<PasskeyCredential>
    suspend fun getHandleByUsername(username: Uuid): Uuid?
    suspend fun getUsernameByHandle(userHandle: Uuid): String?
    suspend fun getCredentialsByUsername(username: Uuid): Set<PasskeyCredential>
    suspend fun getCredentialsByCredentialId(credentialId: ByteArray): Set<PasskeyCredential>
    suspend fun markAsUsed(passkeyCredentialId: Uuid)
    suspend fun deletePasskey(id: Uuid, authId: Uuid): Int
}