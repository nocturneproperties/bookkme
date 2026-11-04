package com.bookk.core.service

import io.ktor.openapi.OpenApiInfo
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.Routing
import io.ktor.server.routing.application
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.routingRoot

fun Routing.installSwagger(config: ServiceConfig) {
    swaggerUI("/${config.root}/internal/swagger") {
        info = OpenApiInfo(
            config.title,
            config.version,
            contact = OpenApiInfo.Contact(
                name = "Michael Antiufieiev",
                email = "antufeevmichael@gmail.com",
            ),
        )
        source = OpenApiDocSource.Routing {
            application.routingRoot.descendants()
        }
    }
}
