package com.bookk.auth.domain.impl.passkey

import com.bookk.core.AppLevelConstants
import com.yubico.webauthn.CredentialRepository
import com.yubico.webauthn.RelyingParty
import com.yubico.webauthn.data.RelyingPartyIdentity

internal fun createRelyingParty(credentialRepository: CredentialRepository) = RelyingParty.builder()
    .identity(
        RelyingPartyIdentity.builder()
            .id(AppLevelConstants.domainName)
            .name(AppLevelConstants.APP_NAME)
            .build()
    )
    .credentialRepository(credentialRepository)
    .origins(
        setOf(
            "android:apk-key-hash:l63z2UoYmCmoJ2V8Z9CfAxPkbhd7IjZvUrihusXdt10",
            "android:apk-key-hash:OCIXBZS_NbVj7Rr8TSctbrMGlxoMyZ0wl6ajVa7Yry4",
            "https://bookkme.app"
        )
    )
    .build()