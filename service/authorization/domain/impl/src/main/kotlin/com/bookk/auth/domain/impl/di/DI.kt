package com.bookk.auth.domain.impl.di

import com.bookk.auth.domain.api.AUTH_SCHEMA
import com.bookk.auth.domain.api.AUTH_SERVICE_NAME
import com.bookk.auth.domain.api.actionconfirmation.operation.ConfirmAction
import com.bookk.auth.domain.api.authentication.operation.FinishAssertion
import com.bookk.auth.domain.api.authentication.operation.HandleDormantAccounts
import com.bookk.auth.domain.api.authentication.operation.SignIn
import com.bookk.auth.domain.api.authentication.operation.StartAssertion
import com.bookk.auth.domain.api.delete_account.operation.CancelAccountDeletion
import com.bookk.auth.domain.api.delete_account.operation.DeleteAccount
import com.bookk.auth.domain.api.delete_account.operation.GetAccountDeletion
import com.bookk.auth.domain.api.delete_account.operation.HandleScheduledAccountDeletions
import com.bookk.auth.domain.api.demo.operation.DeleteExpiredDemoAccounts
import com.bookk.auth.domain.api.demo.operation.FinishDemoUpgrade
import com.bookk.auth.domain.api.demo.operation.StartDemo
import com.bookk.auth.domain.api.demo.operation.StartDemoUpgrade
import com.bookk.auth.domain.api.device.operation.DeleteInactiveDevices
import com.bookk.auth.domain.api.email.operation.ConfirmEmailChange
import com.bookk.auth.domain.api.email.operation.StartEmailChange
import com.bookk.auth.domain.api.erasure.operation.DeleteExpiredErasureRecords
import com.bookk.auth.domain.api.erasure.operation.ReapplyAccountErasures
import com.bookk.auth.domain.api.identification.operation.DeletePasskey
import com.bookk.auth.domain.api.identification.operation.GetAttachPasskeyToAccountChallenge
import com.bookk.auth.domain.api.identification.operation.GetAvailablePasskeys
import com.bookk.auth.domain.api.registration.operation.AttachNewPasskeyToAccount
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration
import com.bookk.auth.domain.api.registration.operation.FinishRegistration
import com.bookk.auth.domain.api.registration.operation.StartPasskeyRegistration
import com.bookk.auth.domain.api.registration.operation.StartRegistration
import com.bookk.auth.domain.api.signout.operation.SignOut
import com.bookk.auth.domain.api.telegram.operation.ConfirmActionWithTelegram
import com.bookk.auth.domain.api.telegram.operation.SignInWithTelegram
import com.bookk.auth.domain.api.telegram.operation.StartTelegramActionConfirmation
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken
import com.bookk.auth.domain.api.token.operation.RefreshToken
import com.bookk.auth.domain.impl.operation.AccountEraser
import com.bookk.auth.domain.impl.operation.CancelAccountDeletionImpl
import com.bookk.auth.domain.impl.operation.DeleteAccountImpl
import com.bookk.auth.domain.impl.operation.DeviceSignOut
import com.bookk.auth.domain.impl.operation.GetAccountDeletionImpl
import com.bookk.auth.domain.impl.operation.HandleScheduledAccountDeletionsImpl
import com.bookk.auth.domain.impl.operation.SignOutImpl
import com.bookk.auth.domain.impl.operation.actionconfirmation.ConfirmActionImpl
import com.bookk.auth.domain.impl.operation.authentication.FinishAssertionImpl
import com.bookk.auth.domain.impl.operation.authentication.HandleDormantAccountsImpl
import com.bookk.auth.domain.impl.operation.authentication.SignInImpl
import com.bookk.auth.domain.impl.operation.authentication.StartAssertionImpl
import com.bookk.auth.domain.impl.operation.demo.DeleteExpiredDemoAccountsImpl
import com.bookk.auth.domain.impl.operation.demo.DemoCallerResolver
import com.bookk.auth.domain.impl.operation.demo.FinishDemoUpgradeImpl
import com.bookk.auth.domain.impl.operation.demo.StartDemoImpl
import com.bookk.auth.domain.impl.operation.demo.StartDemoUpgradeImpl
import com.bookk.auth.domain.impl.operation.device.DeleteInactiveDevicesImpl
import com.bookk.auth.domain.impl.operation.email.ConfirmEmailChangeImpl
import com.bookk.auth.domain.impl.operation.email.StartEmailChangeImpl
import com.bookk.auth.domain.impl.operation.erasure.DeleteExpiredErasureRecordsImpl
import com.bookk.auth.domain.impl.operation.erasure.ReapplyAccountErasuresImpl
import com.bookk.auth.domain.impl.operation.export.ExportAccountDataImpl
import com.bookk.auth.domain.impl.operation.identification.DeletePasskeyImpl
import com.bookk.auth.domain.impl.operation.identification.GetAttachPasskeyToAccountChallengeImpl
import com.bookk.auth.domain.impl.operation.identification.GetAvailablePasskeysImpl
import com.bookk.auth.domain.impl.operation.registration.AttachNewPasskeyToAccountImpl
import com.bookk.auth.domain.impl.operation.registration.FinishPasskeyRegistrationImpl
import com.bookk.auth.domain.impl.operation.registration.FinishRegistrationImpl
import com.bookk.auth.domain.impl.operation.registration.StartPasskeyRegistrationImpl
import com.bookk.auth.domain.impl.operation.registration.StartRegistrationImpl
import com.bookk.auth.domain.impl.operation.telegram.ConfirmActionWithTelegramImpl
import com.bookk.auth.domain.impl.operation.telegram.SignInWithTelegramImpl
import com.bookk.auth.domain.impl.operation.telegram.StartTelegramActionConfirmationImpl
import com.bookk.auth.domain.impl.operation.token.GenerateAuthTokenImpl
import com.bookk.auth.domain.impl.operation.token.RefreshTokenImpl
import com.bookk.auth.domain.impl.telegram.Ed25519TelegramInitDataVerifier
import com.bookk.auth.domain.impl.telegram.TelegramInitDataVerifier
import com.bookk.notifications.client.di.notificationsClientModule
import com.bookk.server.auth.client.export.ExportAccountData
import com.bookk.server.user.client.di.userClientModule
import org.koin.core.module.dsl.scopedOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

val AuthScope: Qualifier = named(AUTH_SCHEMA)

private const val TELEGRAM_BOT_ID = "APPLICATION_TELEGRAM_BOT_ID"

fun authDomainModule(environment: (String) -> String? = System::getenv) = module {
    includes(userClientModule(AuthScope, AUTH_SERVICE_NAME), notificationsClientModule(AuthScope, AUTH_SERVICE_NAME))
    scope(AuthScope) {
        scopedOf(::GenerateAuthTokenImpl) bind GenerateAuthToken::class
        scopedOf(::RefreshTokenImpl) bind RefreshToken::class
        scopedOf(::SignOutImpl) bind SignOut::class
        scopedOf(::AccountEraser)
        scopedOf(::DeviceSignOut)
        scopedOf(::HandleDormantAccountsImpl) bind HandleDormantAccounts::class
        scopedOf(::DeleteAccountImpl) bind DeleteAccount::class
        scopedOf(::GetAccountDeletionImpl) bind GetAccountDeletion::class
        scopedOf(::CancelAccountDeletionImpl) bind CancelAccountDeletion::class
        scopedOf(::HandleScheduledAccountDeletionsImpl) bind HandleScheduledAccountDeletions::class
        scopedOf(::ReapplyAccountErasuresImpl) bind ReapplyAccountErasures::class
        scopedOf(::DeleteExpiredErasureRecordsImpl) bind DeleteExpiredErasureRecords::class
        scopedOf(::ConfirmActionImpl) bind ConfirmAction::class
        scopedOf(::ExportAccountDataImpl) bind ExportAccountData::class
        scopedOf(::StartRegistrationImpl) bind StartRegistration::class
        scopedOf(::FinishRegistrationImpl) bind FinishRegistration::class
        scopedOf(::StartEmailChangeImpl) bind StartEmailChange::class
        scopedOf(::ConfirmEmailChangeImpl) bind ConfirmEmailChange::class
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
        scoped<TelegramInitDataVerifier> {
            Ed25519TelegramInitDataVerifier(botId = environment.telegramBotId(), clock = Clock.System)
        }
        scopedOf(::SignInWithTelegramImpl) bind SignInWithTelegram::class
        scopedOf(::StartTelegramActionConfirmationImpl) bind StartTelegramActionConfirmation::class
        scopedOf(::ConfirmActionWithTelegramImpl) bind ConfirmActionWithTelegram::class
        scopedOf(::StartDemoImpl) bind StartDemo::class
        scopedOf(::DemoCallerResolver)
        scopedOf(::StartDemoUpgradeImpl) bind StartDemoUpgrade::class
        scopedOf(::FinishDemoUpgradeImpl) bind FinishDemoUpgrade::class
        scopedOf(::DeleteExpiredDemoAccountsImpl) bind DeleteExpiredDemoAccounts::class
    }
}

private fun ((String) -> String?).telegramBotId(): Long? {
    val value = this(TELEGRAM_BOT_ID)?.takeIf { it.isNotBlank() } ?: return null
    return value.trim().toLongOrNull() ?: throw IllegalStateException("$TELEGRAM_BOT_ID is not a numeric bot id")
}
