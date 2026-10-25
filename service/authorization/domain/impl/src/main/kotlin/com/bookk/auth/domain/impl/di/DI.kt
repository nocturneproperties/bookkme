package com.bookk.auth.domain.impl.di

import com.bookk.auth.domain.api.AUTH_SCHEMA
import com.bookk.auth.domain.api.AUTH_SERVICE_NAME
import com.bookk.auth.domain.api.authentication.operation.FinishAssertion
import com.bookk.auth.domain.api.authentication.operation.SignIn
import com.bookk.auth.domain.api.authentication.operation.StartAssertion
import com.bookk.auth.domain.api.delete_account.operation.DeleteAccount
import com.bookk.auth.domain.api.device.operation.DeleteInactiveDevices
import com.bookk.auth.domain.api.identification.operation.DeletePasskey
import com.bookk.auth.domain.api.identification.operation.GetAttachPasskeyToAccountChallenge
import com.bookk.auth.domain.api.identification.operation.GetAvailablePasskeys
import com.bookk.auth.domain.api.registration.operation.AttachNewPasskeyToAccount
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration
import com.bookk.auth.domain.api.registration.operation.FinishRegistration
import com.bookk.auth.domain.api.registration.operation.StartPasskeyRegistration
import com.bookk.auth.domain.api.registration.operation.StartRegistration
import com.bookk.auth.domain.api.signout.operation.SignOut
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken
import com.bookk.auth.domain.api.token.operation.RefreshToken
import com.bookk.auth.domain.impl.operation.DeleteAccountImpl
import com.bookk.auth.domain.impl.operation.SignOutImpl
import com.bookk.auth.domain.impl.operation.authentication.FinishAssertionImpl
import com.bookk.auth.domain.impl.operation.authentication.SignInImpl
import com.bookk.auth.domain.impl.operation.authentication.StartAssertionImpl
import com.bookk.auth.domain.impl.operation.device.DeleteInactiveDevicesImpl
import com.bookk.auth.domain.impl.operation.identification.DeletePasskeyImpl
import com.bookk.auth.domain.impl.operation.identification.GetAttachPasskeyToAccountChallengeImpl
import com.bookk.auth.domain.impl.operation.identification.GetAvailablePasskeysImpl
import com.bookk.auth.domain.impl.operation.registration.AttachNewPasskeyToAccountImpl
import com.bookk.auth.domain.impl.operation.registration.FinishPasskeyRegistrationImpl
import com.bookk.auth.domain.impl.operation.registration.FinishRegistrationImpl
import com.bookk.auth.domain.impl.operation.registration.StartPasskeyRegistrationImpl
import com.bookk.auth.domain.impl.operation.registration.StartRegistrationImpl
import com.bookk.auth.domain.impl.operation.token.GenerateAuthTokenImpl
import com.bookk.auth.domain.impl.operation.token.RefreshTokenImpl
import com.bookk.server.user.client.di.userClientModule
import library.signing.impl.di.signingModule
import org.koin.core.module.dsl.scopedOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val AuthScope: Qualifier = named(AUTH_SCHEMA)

fun authDomainModule() = module {
    includes(userClientModule(AuthScope, AUTH_SERVICE_NAME))
    includes(signingModule(AuthScope))
    scope(AuthScope) {
        scopedOf(::GenerateAuthTokenImpl) bind GenerateAuthToken::class
        scopedOf(::RefreshTokenImpl) bind RefreshToken::class
        scopedOf(::SignOutImpl) bind SignOut::class
        scopedOf(::DeleteAccountImpl) bind DeleteAccount::class
        scopedOf(::StartRegistrationImpl) bind StartRegistration::class
        scopedOf(::FinishRegistrationImpl) bind FinishRegistration::class
        scopedOf(::StartAssertionImpl) bind StartAssertion::class
        scopedOf(::FinishAssertionImpl) bind FinishAssertion::class
        scopedOf(::SignInImpl) bind SignIn::class
        scopedOf(::GetAvailablePasskeysImpl) bind GetAvailablePasskeys::class
        scopedOf(::DeletePasskeyImpl) bind DeletePasskey::class
        scopedOf(::GetAttachPasskeyToAccountChallengeImpl) bind GetAttachPasskeyToAccountChallenge::class
        scopedOf(::StartPasskeyRegistrationImpl) bind StartPasskeyRegistration::class
        scopedOf(::FinishPasskeyRegistrationImpl) bind FinishPasskeyRegistration::class
        scopedOf(::AttachNewPasskeyToAccountImpl) bind AttachNewPasskeyToAccount::class
        scopedOf(::DeleteInactiveDevicesImpl) bind DeleteInactiveDevices::class
    }
}