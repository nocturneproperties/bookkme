package com.bookk.server

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LogbackClasspathTest {

    @Test
    fun `should find only the core logback configuration on the monolith classpath`() = runUnitTest {
        given()
        val classLoader = requireNotNull(Thread.currentThread().contextClassLoader)

        whenn()
        val configurations = classLoader.getResources("logback.xml").toList().map { it.toString() }

        then()
        assertEquals(1, configurations.size, configurations.joinToString())
    }
}
