package com.bookk.auth.domain.impl.operation.registration

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.registration.entity.VerifyAccountCreationRequest
import com.bookk.auth.domain.api.registration.entity.VerifyAccountCreationRequest.UserInfo
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration
import com.bookk.auth.domain.api.registration.operation.FinishRegistration
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken
import com.bookk.auth.domain.api.token.operation.GenerateAuthToken.Source
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.auth.client.AuthEvent
import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.api.CreateUserRequest
import kotlin.uuid.Uuid

internal class FinishRegistrationImpl(
    private val accountDataSource: AccountDataSource,
    private val deviceDataSource: DeviceDataSource,
    private val userClient: UserClient,
    private val generateAuthToken: GenerateAuthToken,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
    private val finishPasskeyRegistration: FinishPasskeyRegistration
) : FinishRegistration {

    override suspend fun invoke(request: VerifyAccountCreationRequest, language: Language): Result<AuthTokens> = runSuspendCatching {
        val verifiedPasskey = finishPasskeyRegistration.verifyRequest(request).getOrThrow()
        val userId = userClient.createUser(createUserFrom(request.userInfo)).getOrThrow()
        val deviceName = request.deviceInfo.deviceName
        val deviceUuid = request.deviceInfo.deviceUUID

        return transactionManager.transaction {
            val authorization = Authentication(id = Uuid.random(), userId = userId, uuid = verifiedPasskey.handle)
            val owner = accountDataSource.createAuthorization(authorization)

            finishPasskeyRegistration.attachOwner(owner.id, verifiedPasskey)

            deviceDataSource.insertDevice(
                authId = owner.id,
                uuid = deviceUuid,
                name = deviceName,
                language = language
            ) ?: throw FinishRegistration.Error.AccountCreationFailed()
            eventProducer.send(AuthEvent.DeviceCreated(owner.id, userId, deviceUuid, language))
            generateAuthToken(Source.InitialAuthentication(owner.id, deviceUuid)).getOrThrow()
        }.onFailure {
            eventProducer.send(AuthEvent.UserDeleted(userId))
            eventProducer.send(AuthEvent.DeviceDeleted(deviceUuid))
        }
    }

    private fun createUserFrom(info: UserInfo): CreateUserRequest = CreateUserRequest(
        name = info.name,
        lastName = info.lastName,
        email = info.email
    )
}