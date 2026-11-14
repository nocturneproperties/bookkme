package com.bookk.core.client

import com.bookk.core.AppLevelConstants
import com.bookk.core.AppLevelConstants.SupportedSerializers
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
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
import kotlin.uuid.Uuid

fun internalHttpClient(hostnameVariable: String, clientTag: String): HttpClient = internalHttpClient(
    CIO,
    hostnameVariable,
    clientTag,
)

fun <T : HttpClientEngineConfig> internalHttpClient(
    engineFactory: HttpClientEngineFactory<T>,
    hostnameVariable: String,
    clientTag: String,
    environment: (String) -> String? = System::getenv,
    engine: T.() -> Unit = {},
): HttpClient {
    val hostname = environment(hostnameVariable)
    return HttpClient(engineFactory) {
        engine(engine)
        install(Resources)
        install(UserAgent) { agent = clientTag }
        install(HttpRequestRetry) {
            retryOnExceptionOrServerErrors(maxRetries = 3)
            constantDelay(millis = 50, randomizationMs = 100)
        }
        install(ContentNegotiation) {
            when (AppLevelConstants.SERIALIZER) {
                SupportedSerializers.JSON.STR -> json(
                    Json {
                        prettyPrint = true
                        encodeDefaults = true
                        explicitNulls = false
                    },
                )

                SupportedSerializers.PROTOBUF.STR -> protobuf(ProtoBuf { encodeDefaults = true })
            }
        }
        installInternalCallLogging()
        defaultRequest {
            host =
                hostname
                    ?: throw IllegalStateException(
                        "$hostnameVariable is not set, so $clientTag can't call that service",
                    )
            headers["Idempotency-Key"] = Uuid.random().toString()
            url { protocol = URLProtocol.HTTP }
            when (AppLevelConstants.SERIALIZER) {
                SupportedSerializers.JSON.STR -> contentType(ContentType.Application.Json)
                SupportedSerializers.PROTOBUF.STR -> contentType(ContentType.Application.ProtoBuf)
            }
        }
    }
}
