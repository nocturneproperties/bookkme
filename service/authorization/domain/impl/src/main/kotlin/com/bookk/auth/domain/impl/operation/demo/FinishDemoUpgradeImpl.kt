package com.bookk.auth.domain.impl.operation.demo

import com.bookk.auth.domain.api.demo.entity.DemoAccountPolicy
import com.bookk.auth.domain.api.demo.entity.DemoCaller
import com.bookk.auth.domain.api.demo.entity.DemoUpgradeFinishRequest
import com.bookk.auth.domain.api.demo.operation.DemoUpgradeError.EmailAlreadyExist
import com.bookk.auth.domain.api.demo.operation.DemoUpgradeError.NotDemoAccount
import com.bookk.auth.domain.api.demo.operation.FinishDemoUpgrade
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration.Error.ChallengeWindowExpired
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration.Error.VerificationFailed
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken.Error.DemoExpired
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken.Source
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.impl.email.EmailPurpose
import com.bookk.auth.domain.impl.operation.registration.PersonName
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.auth.client.AuthEvent
import com.bookk.server.user.client.UserClient
import com.bookk.user.domain.api.entity.ProfileName
import com.bookk.user.domain.api.error.UserErrorCodes
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import library.email.EmailCodeError
import library.email.EmailCodeRedeemer
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class FinishDemoUpgradeImpl(
    private val demoCallerResolver: DemoCallerResolver,
    private val accountDataSource: AccountDataSource,
    private val userClient: UserClient,
    private val finishPasskeyRegistration: FinishPasskeyRegistration,
    private val emailCodeRedeemer: EmailCodeRedeemer,
    private val generateAuthToken: GenerateAuthToken,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
    meterRegistry: MeterRegistry,
) : FinishDemoUpgrade {

    private val upgradedDemos = Counter.builder(DEMO_UPGRADED_METRIC)
        .description("Demo accounts upgraded to regular accounts")
        .register(meterRegistry)

    override suspend fun invoke(
        caller: DemoCaller,
        request: DemoUpgradeFinishRequest,
    ): Result<AuthTokens> = runSuspendCatching {
        val upgradeAttempt = Uuid.parseOrNull(request.requestId) ?: throw ChallengeWindowExpired()
        val demo = demoCallerResolver.resolve(caller)
        emailCodeRedeemer.redeem(request.verificationId, request.emailCode, ownerId = upgradeAttempt) { verified ->
            if (verified.purpose != EmailPurpose.DEMO_UPGRADE) throw EmailCodeError.Invalid()
            upgrade(demo, request, verified.email)
        }.getOrThrow().also { upgradedDemos.increment() }
    }

    private suspend fun upgrade(demo: DemoSession, request: DemoUpgradeFinishRequest, email: String): AuthTokens {
        val passkey = finishPasskeyRegistration.verifyRequest(request).getOrThrow()
        if (passkey.handle != demo.account.uuid) throw VerificationFailed()
        storeProfile(demo.account.userId, email, request.name)
        return transactionManager.transactionOrThrow { endDemo(demo, passkey) }
    }

    private suspend fun storeProfile(userId: Uuid, email: String, name: String) {
        val personName = PersonName.of(name)
        userClient.upgradeDemoProfile(userId, email, ProfileName(personName.name, personName.lastName))
            .getOrElse { failure -> throw if (failure.isAddressTaken()) EmailAlreadyExist() else failure }
    }

    private suspend fun endDemo(demo: DemoSession, passkey: PasskeyCredential): AuthTokens {
        val locked = accountDataSource.getDemoAccountForUpdate(demo.account.id) ?: throw NotDemoAccount()
        if (Clock.System.now() >= DemoAccountPolicy.upgradeDeadline(locked.expiresAt)) throw DemoExpired()
        accountDataSource.endDemo(locked.authId)
        finishPasskeyRegistration.attachOwner(locked.authId, passkey)
        eventProducer.send(AuthEvent.DemoUpgraded(locked.userId))
        return generateAuthToken(Source.InitialAuthentication(locked.authId, demo.deviceUuid)).getOrThrow()
    }

    private fun Throwable.isAddressTaken(): Boolean {
        return this is BusinessError && code == UserErrorCodes.USER_CONTACT_TAKEN
    }

    companion object {
        const val DEMO_UPGRADED_METRIC = "auth.demo.upgraded"
    }
}
