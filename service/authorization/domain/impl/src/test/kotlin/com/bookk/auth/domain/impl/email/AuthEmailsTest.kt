package com.bookk.auth.domain.impl.email

import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AuthEmailsTest {

    @Test
    fun `should put the sign-up code in the text and html of every language`() = runUnitTest {
        given()
        val code = "048213"

        whenn()
        val emails = Language.entries.map { AuthEmails.signUpCode(it, code) }

        then()
        assertTrue(
            emails.all {
                code in it.textBody && code in it.htmlBody && it.subject.isNotBlank()
            },
        ) { emails.toString() }
        assertTrue(emails.all { it.tag == "sign-up-code" }) { emails.toString() }
    }

    @Test
    fun `should put the email change code in the text and html of every language`() = runUnitTest {
        given()
        val code = "771920"

        whenn()
        val emails = Language.entries.map { AuthEmails.emailChangeCode(it, code) }

        then()
        assertTrue(
            emails.all {
                code in it.textBody && code in it.htmlBody && it.subject.isNotBlank()
            },
        ) { emails.toString() }
        assertTrue(emails.all { it.tag == "email-change-code" }) { emails.toString() }
    }

    @Test
    fun `should word each language differently`() = runUnitTest {
        given()
        val code = "123456"

        whenn()
        val subjects = Language.entries.map { AuthEmails.signUpCode(it, code).subject }

        then()
        assertEquals(Language.entries.size, subjects.toSet().size) { subjects.toString() }
    }

    @Test
    fun `should tell sign-up and email change apart`() = runUnitTest {
        given()
        val code = "123456"

        whenn()
        val signUp = AuthEmails.signUpCode(Language.EN, code)
        val change = AuthEmails.emailChangeCode(Language.EN, code)

        then()
        assertNotEquals(signUp.subject, change.subject)
    }
}
