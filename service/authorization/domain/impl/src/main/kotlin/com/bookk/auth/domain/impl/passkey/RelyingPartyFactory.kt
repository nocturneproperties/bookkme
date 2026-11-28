package com.bookk.auth.domain.impl.passkey

import com.bookk.core.AppLevelConstants
import com.yubico.webauthn.CredentialRepository
import com.yubico.webauthn.RelyingParty
import com.yubico.webauthn.data.RelyingPartyIdentity

private val ANDROID_APP_ORIGINS = setOf(
    "android:apk-key-hash:l63z2UoYmCmoJ2V8Z9CfAxPkbhd7IjZvUrihusXdt10",
    "android:apk-key-hash:OCIXBZS_NbVj7Rr8TSctbrMGlxoMyZ0wl6ajVa7Yry4",
)

internal fun createRelyingParty(credentialRepository: CredentialRepository): RelyingParty {
    val origins = passkeyOrigins(
        domainName = AppLevelConstants.domainName,
        siteDomainName = AppLevelConstants.siteDomainName,
        extraOrigins = AppLevelConstants.passkeyExtraOrigins,
    )
    val identity = RelyingPartyIdentity.builder()
        .id(AppLevelConstants.domainName)
        .name(AppLevelConstants.APP_NAME)
        .build()
    return RelyingParty.builder()
        .identity(identity)
        .credentialRepository(credentialRepository)
        .origins(origins)
        .build()
}

internal fun passkeyOrigins(domainName: String, siteDomainName: String, extraOrigins: String): Set<String> {
    val siteOrigins = siteDomainName.takeIf { it.isNotBlank() }
        ?.let { setOf("https://$it", "https://www.$it", "https://app.$it") }
        .orEmpty()
    val listedOrigins = extraOrigins.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return ANDROID_APP_ORIGINS + "https://$domainName" + siteOrigins + listedOrigins
}
