package com.bookk.server.user.client.di

import com.bookk.core.AppLevelConstants
import com.bookk.core.AppLevelConstants.SupportedSerializers
import com.bookk.core.client.installInternalCallLogging
import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.impl.UserClientImpl
import com.bookk.server.user.client.impl.operation.CreateUserClientImpl
import com.bookk.server.user.client.impl.operation.DeleteUserClientImpl
import com.bookk.server.user.client.impl.operation.GetUserByEmailClientImpl
import com.bookk.server.user.client.impl.operation.GetUserByIdClientImpl
import com.bookk.user.domain.api.operation.CreateUser
import com.bookk.user.domain.api.operation.DeleteUser
import com.bookk.user.domain.api.operation.GetUserByEmail
import com.bookk.user.domain.api.operation.GetUserById
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.http.ContentType
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.protobuf.protobuf
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.core.qualifier.Qualifier
import org.koin.dsl.module
import kotlin.uuid.Uuid

@Suppress("KotlinConstantConditions")
fun userClientModule(qualifier: Qualifier, clientTag: String) = module {
    scope(qualifier) {
        scoped {
            HttpClient(CIO) {
                install(Resources)
                install(UserAgent) { agent = clientTag }
                install(HttpRequestRetry) {
                    retryOnExceptionOrServerErrors(maxRetries = 3)
                    constantDelay(millis = 50, randomizationMs = 100)
                }
                install(ContentNegotiation) {
                    when (AppLevelConstants.SERIALIZER) {
                        SupportedSerializers.JSON.STR -> {
                            json(Json {
                                prettyPrint = true
                                encodeDefaults = true
                                explicitNulls = false
                            })
                        }

                        SupportedSerializers.PROTOBUF.STR -> {
                            protobuf(ProtoBuf { encodeDefaults = true })
                        }
                    }
                }
                installInternalCallLogging()
                defaultRequest {
                    host = System.getenv("APPLICATION_USER_SERVICE_HOSTNAME")

                    headers["Idempotency-Key"] = Uuid.random().toString()

                    url { protocol = URLProtocol.HTTP }

                    when (AppLevelConstants.SERIALIZER) {
                        SupportedSerializers.JSON.STR -> contentType(ContentType.Application.Json)
                        SupportedSerializers.PROTOBUF.STR -> contentType(ContentType.Application.ProtoBuf)
                    }
                }
            }
        }
        scoped<GetUserById> { GetUserByIdClientImpl(get()) }
        scoped<CreateUser> { CreateUserClientImpl(get()) }
        scoped<DeleteUser> { DeleteUserClientImpl(get()) }
        scoped<GetUserByEmail> { GetUserByEmailClientImpl(get()) }
        scoped<UserClient> { UserClientImpl(get(), get(), get(), get()) }
    }
}
