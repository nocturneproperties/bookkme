package com.bookk.core.service.test

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.protobuf.protobuf
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.protobuf.ProtoBuf

fun ApplicationTestBuilder.createTestClient(): HttpClient = createClient {
    install(ContentNegotiation) {
        protobuf(ProtoBuf { encodeDefaults = true })
    }
    install(Resources)
    defaultRequest {
        contentType(ContentType.Application.ProtoBuf)
    }
}
