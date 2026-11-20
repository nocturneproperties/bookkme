package com.bookk.auth.domain.impl.operation.telegram

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.telegram.entity.TelegramSignInRequest
import com.bookk.auth.domain.api.telegram.entity.TelegramSignInRequest.DeviceInfo
import com.bookk.auth.domain.api.telegram.entity.TelegramUser
import com.bookk.auth.domain.api.telegram.operation.SignInWithTelegram
import com.bookk.auth.domain.api.telegram.operation.SignInWithTelegram.Error.AccountCreationFailed
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken.Source
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.auth.domain.datasource.TelegramIdentityDataSource
import com.bookk.auth.domain.impl.telegram.TelegramInitDataVerifier
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.auth.client.AuthEvent
import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.api.CreateUserRequest
import com.bookk.user.domain.api.operation.CreateUser
import library.email.PlaceholderEmail
import kotlin.uuid.Uuid

internal class SignInWithTelegramImpl(
    private val verifier: TelegramInitDataVerifier,
    private val telegramIdentityDataSource: TelegramIdentityDataSource,
    private val accountDataSource: AccountDataSource,
    private val deviceDataSource: DeviceDataSource,
    private val userClient: UserClient,
    private val generateAuthToken: GenerateAuthToken,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
) : SignInWithTelegram {

    override suspend fun invoke(
        request: TelegramSignInRequest,
        language: Language,
    ): Result<AuthTokens> = runSuspendCatching {
        val telegramUser = verifier.verify(request.initData)
        val account = transactionManager.transactionOrThrow {
            telegramIdentityDataSource.getAccountByTelegramUser(telegramUser.id)
        }
        if (account != null) {
            signIn(account, request.deviceInfo, language)
        } else {
            register(telegramUser, request, language)
        }
    }

    private suspend fun signIn(
        account: Authentication,
        deviceInfo: DeviceInfo,
        language: Language,
    ): AuthTokens = transactionManager.transactionOrThrow {
        val newDeviceId = deviceDataSource.insertDevice(
            authId = account.id,
            uuid = deviceInfo.deviceUUID,
            name = deviceInfo.deviceName,
            language = language,
        )
        if (newDeviceId != null) {
            eventProducer.send(AuthEvent.DeviceCreated(account.id, account.userId, deviceInfo.deviceUUID, language))
        } else {
            deviceDataSource.updateLanguage(account.id, deviceInfo.deviceUUID, language)
            eventProducer.send(AuthEvent.DeviceLanguageUpdated(deviceInfo.deviceUUID, language, authId = account.id))
        }
        generateAuthToken(Source.InitialAuthentication(account.id, deviceInfo.deviceUUID)).getOrThrow()
    }

    private suspend fun register(
        telegramUser: TelegramUser,
        request: TelegramSignInRequest,
        language: Language,
    ): AuthTokens {
        val userId = userClient.createUser(createUserFrom(telegramUser, request)).getOrThrow()
        val deviceUuid = request.deviceInfo.deviceUUID
        return transactionManager.transaction {
            val account = accountDataSource.createAuthorization(
                Authentication(id = Uuid.random(), userId = userId, uuid = Uuid.random()),
            )
            telegramIdentityDataSource.link(account.id, telegramUser.id)
            deviceDataSource.insertDevice(
                authId = account.id,
                uuid = deviceUuid,
                name = request.deviceInfo.deviceName,
                language = language,
            ) ?: throw AccountCreationFailed()
            eventProducer.send(AuthEvent.DeviceCreated(account.id, userId, deviceUuid, language))
            generateAuthToken(Source.InitialAuthentication(account.id, deviceUuid)).getOrThrow()
        }.onFailure {
            transactionManager.transaction { eventProducer.send(AuthEvent.UserDeleted(userId)) }
        }.getOrThrow()
    }

    private fun createUserFrom(telegramUser: TelegramUser, request: TelegramSignInRequest): CreateUserRequest {
        return CreateUserRequest(
            name = telegramUser.firstName,
            lastName = telegramUser.lastName.orEmpty(),
            email = PlaceholderEmail.forTelegramUser(telegramUser.id),
            acceptedDocuments = request.acceptedDocuments ?: throw CreateUser.Error.LegalDocumentsNotAccepted(),
            telegramUserId = telegramUser.id,
        )
    }
}
