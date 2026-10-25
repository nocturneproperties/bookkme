package com.bookk.core.data.database

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class PoolConfigTest {

    private val swarmVirtualIpIdleTimeout = 15.minutes
    private val hikariMinimumKeepalive = 30.seconds

    private class SutFixture {
        val config = poolConfig(
            schemaName = "schema",
            dbUrl = "mariadb://localhost",
            dbPort = "3306",
            dbUsername = "user",
            dbPassword = "password",
            maxPoolSize = 10
        )
    }

    @Test
    fun `should ping idle connections before the swarm virtual ip forgets them`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val keepalive = fixture.config.keepaliveTime.milliseconds

        then()
        assertTrue(keepalive >= hikariMinimumKeepalive)
        assertTrue(keepalive < swarmVirtualIpIdleTimeout)
    }

    @Test
    fun `should ping idle connections more often than they are retired`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val keepalive = fixture.config.keepaliveTime.milliseconds
        val maxLifetime = fixture.config.maxLifetime.milliseconds

        then()
        assertTrue(keepalive < maxLifetime)
    }
}
