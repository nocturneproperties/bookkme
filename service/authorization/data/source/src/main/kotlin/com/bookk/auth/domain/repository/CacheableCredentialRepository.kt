package com.bookk.auth.domain.repository

import com.yubico.webauthn.CredentialRepository
import com.yubico.webauthn.data.ByteArray as YubicoByteArray

interface CacheableCredentialRepository : CredentialRepository {
    suspend fun cacheCredentialIdsForUsername(username: String)
    suspend fun cacheUserHandle(username: String)
    suspend fun cacheUsername(handle: YubicoByteArray)
    suspend fun lookupCache(credentialId: YubicoByteArray, handle: YubicoByteArray)
    suspend fun lookupAllCache(credentialId: YubicoByteArray)
}