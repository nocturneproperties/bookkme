package com.bookk.auth.domain.impl.operation.demo

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.demo.entity.DemoAccountPolicy
import com.bookk.auth.domain.api.demo.entity.DemoStartRequest
import com.bookk.auth.domain.api.demo.operation.StartDemo
import com.bookk.auth.domain.api.demo.operation.StartDemo.Error.AccountCreationFailed
import com.bookk.auth.domain.api.demo.operation.StartDemo.Error.DemoUnavailable
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken.Source
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.DemoSettingsDataSource
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.auth.client.AuthEvent
import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.api.CreateUserRequest
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.datetime.TimeZone
import library.email.PlaceholderEmail
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class StartDemoImpl(
    private val accountDataSource: AccountDataSource,
    private val deviceDataSource: DeviceDataSource,
    private val demoSettingsDataSource: DemoSettingsDataSource,
    private val userClient: UserClient,
    private val generateAuthToken: GenerateAuthToken,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
    meterRegistry: MeterRegistry,
) : StartDemo {

    private val startedDemos = Counter.builder(DEMO_STARTED_METRIC)
        .description("Demo accounts started")
        .register(meterRegistry)

    override suspend fun invoke(
        request: DemoStartRequest,
        language: Language,
    ): Result<AuthTokens> = runSuspendCatching {
        val timeZoneId = knownTimeZoneOrUtc(request.timeZone)
        val now = Clock.System.now()
        val hasRoom = transactionManager.transactionOrThrow {
            accountDataSource.countDemoAccountsActiveAt(now) < demoSettingsDataSource.getMaxActiveAccounts()
        }
        if (!hasRoom) throw DemoUnavailable()
        val userId = userClient.createUser(placeholderUser(request)).getOrThrow()
        createAccount(userId, request.copy(timeZone = timeZoneId), language, expiresAt = now + DemoAccountPolicy.LENGTH)
            .also { startedDemos.increment() }
    }

    private suspend fun createAccount(
        userId: Uuid,
        request: DemoStartRequest,
        language: Language,
        expiresAt: Instant,
    ): AuthTokens {
        val deviceUuid = request.deviceInfo.deviceUUID
        return transactionManager.transaction {
            val account = accountDataSource.createAuthorization(
                Authentication(id = Uuid.random(), userId = userId, uuid = Uuid.random(), demoExpiresAt = expiresAt),
            )
            deviceDataSource.insertDevice(
                authId = account.id,
                uuid = deviceUuid,
                name = request.deviceInfo.deviceName,
                language = language,
            ) ?: throw AccountCreationFailed()
            eventProducer.send(AuthEvent.DeviceCreated(account.id, userId, deviceUuid, language))
            eventProducer.send(AuthEvent.DemoAccountCreated(userId, expiresAt, request.timeZone, language))
            generateAuthToken(Source.InitialAuthentication(account.id, deviceUuid)).getOrThrow()
        }.onFailure {
            transactionManager.transaction { eventProducer.send(AuthEvent.UserDeleted(userId)) }
        }.getOrThrow()
    }

    private fun knownTimeZoneOrUtc(id: String): String {
        return runCatching { TimeZone.of(id).id }.getOrDefault(TimeZone.UTC.id)
    }

    private fun placeholderUser(request: DemoStartRequest): CreateUserRequest {
        return CreateUserRequest(
            name = PLACEHOLDER_NAME,
            lastName = "",
            email = PlaceholderEmail.forDemoUser(Uuid.random()),
            acceptedDocuments = request.acceptedDocuments,
            telegramUserId = null,
            telegramUsername = null,
        )
    }

    companion object {
        const val DEMO_STARTED_METRIC = "auth.demo.started"
        private const val PLACEHOLDER_NAME = "Demo"
    }
}
