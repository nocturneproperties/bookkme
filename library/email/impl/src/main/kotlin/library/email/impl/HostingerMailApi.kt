package library.email.impl

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

internal const val HOSTINGER_MAIL_API_URL = "https://api.mail.hostinger.com/api/v1"

private const val MAX_RETRIES = 2

internal fun hostingerMailHttpClient(
    engine: HttpClientEngine,
    retryDelay: suspend (Long) -> Unit = { delay(it.milliseconds) },
): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpRequestRetry) {
        retryIf(MAX_RETRIES) { _, response ->
            response.status == HttpStatusCode.TooManyRequests ||
                response.status.value >= 500
        }
        retryOnException(MAX_RETRIES, retryOnTimeout = true)
        exponentialDelay()
        delay(retryDelay)
    }
}
