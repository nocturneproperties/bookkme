package com.bookk.notifications.microservice

import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.data.test.serviceScopeWithMockedInfrastructure
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.impl.di.NotificationsScope
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.core.scope.Scope

internal class NotificationsModuleTest {

    private class SutFixture(serviceModule: Module) {
        val scope: Scope = serviceScopeWithMockedInfrastructure(NotificationsScope, serviceModule)
    }

    @Test
    fun `should bind no event producer in the standalone microservice`() = runUnitTest {
        given()
        val fixture = SutFixture(notificationsModule())

        whenn()
        val producer = fixture.scope.getOrNull<StandardEventProducer>()

        then()
        assertNull(producer)
    }

    @Test
    fun `should bind no event producer in the monolith`() = runUnitTest {
        given()
        val fixture = SutFixture(notificationsEmbeddedModule())

        whenn()
        val producer = fixture.scope.getOrNull<StandardEventProducer>()

        then()
        assertNull(producer)
    }
}
