package com.bookk.core.service

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseBodyReadyForSend
import io.ktor.server.application.install
import io.ktor.server.request.httpMethod
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.path
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.writer
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

private const val RESPONSE_SIZE_METRIC = "http.server.response.size"
private const val UNROUTED = "unrouted"
private val routeKey = AttributeKey<String>("responseSizeRoute")

internal class ResponseSizeMetricsConfig {
    var registry: MeterRegistry? = null
}

private data class ResponseSizeKey(val method: String, val route: String)

private class ResponseSizeSummaries(private val registry: MeterRegistry) {
    private val summaries = ConcurrentHashMap<ResponseSizeKey, DistributionSummary>()

    fun of(key: ResponseSizeKey): DistributionSummary {
        return summaries.computeIfAbsent(key) {
            DistributionSummary.builder(RESPONSE_SIZE_METRIC)
                .description("Response body bytes per route")
                .baseUnit("bytes")
                .tag("method", key.method)
                .tag("route", key.route)
                .register(registry)
        }
    }
}

private class MeasuredContent(
    private val original: OutgoingContent,
    private val summary: DistributionSummary,
) : OutgoingContent.WriteChannelContent() {
    override val contentType: ContentType?
        get() = original.contentType
    override val status: HttpStatusCode?
        get() = original.status
    override val headers: Headers
        get() = original.headers

    override fun <T : Any> getProperty(key: AttributeKey<T>): T? {
        return original.getProperty(key)
    }

    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) {
        original.setProperty(key, value)
    }

    override suspend fun writeTo(channel: ByteWriteChannel) {
        val bytes = when (original) {
            is ReadChannelContent -> original.readFrom().copyTo(channel)
            is WriteChannelContent -> copyWritten(original, channel)
            else -> 0L
        }
        summary.record(bytes.toDouble())
    }

    private suspend fun copyWritten(content: WriteChannelContent, channel: ByteWriteChannel): Long = coroutineScope {
        val written = writer { content.writeTo(this.channel) }.channel
        written.copyTo(channel)
    }
}

private val ResponseSizeMetrics = createApplicationPlugin("ResponseSizeMetrics", ::ResponseSizeMetricsConfig) {
    val registry = requireNotNull(pluginConfig.registry) { "ResponseSizeMetrics needs a meter registry" }
    val summaries = ResponseSizeSummaries(registry)

    application.monitor.subscribe(RoutingRoot.RoutingCallStarted) { call ->
        call.attributes.put(routeKey, call.route.path)
    }

    on(ResponseBodyReadyForSend) { call, content ->
        val route = call.attributes.getOrNull(routeKey) ?: UNROUTED
        val summary = summaries.of(ResponseSizeKey(call.request.httpMethod.value, route))
        val knownLength = content.contentLength
        when {
            knownLength != null -> summary.record(knownLength.toDouble())

            content is OutgoingContent.ReadChannelContent || content is OutgoingContent.WriteChannelContent -> {
                transformBodyTo(MeasuredContent(content, summary))
            }

            else -> summary.record(0.0)
        }
    }
}

internal fun Application.installResponseSizeMetrics(registry: MeterRegistry) {
    install(ResponseSizeMetrics) {
        this.registry = registry
    }
}
