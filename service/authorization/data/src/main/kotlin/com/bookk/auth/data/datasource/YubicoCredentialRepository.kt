package com.bookk.auth.data.datasource

import com.bookk.auth.data.map.asPublicKeyCredentialDescriptor
import com.bookk.auth.data.map.asRegisteredCredential
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.auth.domain.datasource.PassKeyDataSource
import com.bookk.auth.domain.repository.CacheableCredentialRepository
import com.bookk.core.toUUID
import com.yubico.webauthn.RegisteredCredential
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import com.yubico.webauthn.data.ByteArray as YubicoByteArray


internal class YubicoCredentialRepository(
    private val passKeyDataSource: PassKeyDataSource
) : CacheableCredentialRepository {

    private val credentialsCache = ConcurrentHashMap<String, MutableSet<PublicKeyCredentialDescriptor>>()
    private val userHandleCache = ConcurrentHashMap<String, Optional<YubicoByteArray>>()
    private val usernameCache = ConcurrentHashMap<YubicoByteArray, Optional<String>>()
    private val lookupCache = ConcurrentHashMap<Pair<YubicoByteArray, YubicoByteArray>, Optional<RegisteredCredential>>()
    private val lookupAllCache = ConcurrentHashMap<YubicoByteArray, MutableSet<RegisteredCredential>>()

    override suspend fun cacheCredentialIdsForUsername(username: String) {
        credentialsCache[username] = passKeyDataSource.getCredentialsByUsername(username.toUUID())
            .map(PasskeyCredential::asPublicKeyCredentialDescriptor)
            .toMutableSet()
    }

    override suspend fun cacheUserHandle(username: String) {
        userHandleCache[username] = Optional.ofNullable(
            passKeyDataSource.getHandleByUsername(username.toUUID())?.let {
                YubicoByteArray(it.toByteArray())
            }
        )
    }

    override suspend fun cacheUsername(handle: YubicoByteArray) {
        val username = Optional.ofNullable(
            passKeyDataSource.getUsernameByHandle(handle.bytes.toUUID())
        )
        usernameCache[handle] = username
        if (username.isPresent) {
            userHandleCache[username.get()] = Optional.of(handle)
        }
    }

    override suspend fun lookupCache(credentialId: YubicoByteArray, handle: YubicoByteArray) {
        lookupCache[credentialId to handle] = Optional.ofNullable(
            passKeyDataSource.getCredentialBy(
                userHandle = handle.bytes.toUUID(),
                credentialId = credentialId.bytes
            )?.asRegisteredCredential()
        )
    }

    override suspend fun lookupAllCache(credentialId: YubicoByteArray) {
        lookupAllCache[credentialId] = passKeyDataSource.getCredentialsByCredentialId(credentialId.bytes)
            .map(PasskeyCredential::asRegisteredCredential)
            .toMutableSet()
    }

    override fun getCredentialIdsForUsername(username: String): MutableSet<PublicKeyCredentialDescriptor> {
        return credentialsCache.getValue(username)
    }

    override fun getUserHandleForUsername(username: String): Optional<YubicoByteArray> {
        return userHandleCache.getValue(username)
    }

    override fun getUsernameForUserHandle(handle: YubicoByteArray): Optional<String> {
        return usernameCache.getValue(handle)
    }

    override fun lookup(credentialId: YubicoByteArray, handle: YubicoByteArray): Optional<RegisteredCredential> {
        return lookupCache.getValue(credentialId to handle)
    }

    override fun lookupAll(credentialId: YubicoByteArray): MutableSet<RegisteredCredential> {
        return lookupAllCache.getValue(credentialId)
    }
}