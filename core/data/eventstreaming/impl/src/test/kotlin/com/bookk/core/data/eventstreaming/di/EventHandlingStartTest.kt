package com.bookk.core.data.eventstreaming.di

import com.bookk.core.data.eventstreaming.EventHandler
import com.bookk.core.data.eventstreaming.ExhaustedEventReplayer
import com.bookk.core.data.eventstreaming.startEventHandling
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.util.concurrent.ConcurrentLinkedQueue

internal class EventHandlingStartTest {

    private class SutFixture {
        val started = ConcurrentLinkedQueue<String>()

        fun serviceScopes(vararg services: String, withReplayer: Boolean = true): List<Scope> {
            val koin = koinApplication {
                modules(services.map { service ->
                    module {
                        scope(named(service)) {
                            scoped<EventHandler> { handler(service) }
                            if (withReplayer) scoped<ExhaustedEventReplayer> { replayer(service) }
                        }
                    }
                })
            }.koin
            return services.map { koin.createScope("$it-scope", named(it)) }
        }

        private fun handler(service: String) = object : EventHandler {
            override fun start(scope: CoroutineScope) {
                started += "$service-handler"
            }
        }

        private fun replayer(service: String) = object : ExhaustedEventReplayer {
            override fun start(scope: CoroutineScope): Job {
                started += "$service-replayer"
                return Job()
            }
        }
    }

    @Test
    fun `should start replaying parked events only after every service's event handlers have started`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val scopes = fixture.serviceScopes("user", "business")

        whenn()
        startEventHandling(backgroundScope, scopes)

        then()
        assertEquals(listOf("user-handler", "business-handler", "user-replayer", "business-replayer"), fixture.started.toList())
    }

    @Test
    fun `should start the event handlers of a service without a replayer`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val scopes = fixture.serviceScopes("user", withReplayer = false)

        whenn()
        startEventHandling(backgroundScope, scopes)

        then()
        assertEquals(listOf("user-handler"), fixture.started.toList())
    }
}
