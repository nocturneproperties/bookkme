package com.bookk.core.service.test

import com.bookk.core.service.installNegotiation
import com.bookk.core.test.TestHolder
import com.bookk.core.test.runUnitTest
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.resources.Resources
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.koin.core.module.Module
import org.koin.ktor.plugin.Koin

fun Application.installTestPlugins() {
    install(Resources)
}

fun ApplicationTestBuilder.setupApplication(
    extension: (Application.() -> Unit)? = null,
    diModule: Module,
    routeUnderTest: Routing.() -> Unit
) {
    application {
        installTestPlugins()
        install(Koin) {
            modules(listOf(diModule))
        }
        extension?.invoke(this)
        routing {
            installNegotiation()
            routeUnderTest()
        }
    }
}

fun routeTest(body: suspend ApplicationTestBuilder.() -> Unit) {
    val context = TestHolder()
    runUnitTest(context) {
        testApplication(context) {
            body(this)
        }
    }
}