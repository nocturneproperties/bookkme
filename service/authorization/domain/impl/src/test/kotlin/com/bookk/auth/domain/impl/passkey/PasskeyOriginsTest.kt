package com.bookk.auth.domain.impl.passkey

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PasskeyOriginsTest {

    @Test
    fun `should accept the api domain and the website's domain with and without www`() = runUnitTest {
        given()
        val domainName = "bookkme.app"
        val siteDomainName = "bookkme.com"

        whenn()
        val origins = passkeyOrigins(domainName, siteDomainName)

        then()
        assertEquals(
            setOf("https://bookkme.app", "https://bookkme.com", "https://www.bookkme.com"),
            origins.filter { it.startsWith("https://") }.toSet(),
        )
    }

    @Test
    fun `should accept only the api domain when no website domain is set`() = runUnitTest {
        given()
        val domainName = "booking.example.org"

        whenn()
        val origins = passkeyOrigins(domainName, siteDomainName = "")

        then()
        assertEquals(setOf("https://booking.example.org"), origins.filter { it.startsWith("https://") }.toSet())
    }

    @Test
    fun `should keep accepting the android app's signing certificates`() = runUnitTest {
        given()
        val domainName = "bookkme.app"

        whenn()
        val origins = passkeyOrigins(domainName, siteDomainName = "")

        then()
        assertTrue("android:apk-key-hash:l63z2UoYmCmoJ2V8Z9CfAxPkbhd7IjZvUrihusXdt10" in origins)
        assertTrue("android:apk-key-hash:OCIXBZS_NbVj7Rr8TSctbrMGlxoMyZ0wl6ajVa7Yry4" in origins)
    }
}
