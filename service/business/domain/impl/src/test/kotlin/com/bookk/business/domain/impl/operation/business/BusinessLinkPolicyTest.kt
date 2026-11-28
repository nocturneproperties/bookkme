package com.bookk.business.domain.impl.operation.business

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class BusinessLinkPolicyTest {

    @Test
    fun `should accept lowercase letters and digits joined by single hyphens`() = runUnitTest {
        given()
        val names = listOf("anna-barber", "studio42", "a1-b2-c3")

        whenn()
        val valid = names.associateWith(BusinessLinkPolicy::isValid)

        then()
        assertEquals(names.associateWith { true }, valid)
    }

    @Test
    fun `should never accept an underscore, so no link name can look like a Telegram operation launch`() =
        runUnitTest {
            given()
            val names = listOf(
                "op_delete-account",
                "op_export-personal_0123456789abcdef0123456789abcdef",
                "anna_barber",
                "op_",
            )

            whenn()
            val valid = names.filter(BusinessLinkPolicy::isValid)

            then()
            assertTrue(valid.isEmpty()) { valid.toString() }
        }

    @Test
    fun `should reserve demo and every name starting with demo and a hyphen`() = runUnitTest {
        given()
        val names = listOf("demo", "demo-x", "demo-abcd1234")

        whenn()
        val reserved = names.associateWith(BusinessLinkPolicy::isReserved)

        then()
        assertEquals(names.associateWith { true }, reserved)
    }

    @Test
    fun `should not reserve names that only start with the letters demo`() = runUnitTest {
        given()
        val names = listOf("demon-tattoo", "demolition", "my-demo")

        whenn()
        val reserved = names.filter(BusinessLinkPolicy::isReserved)

        then()
        assertTrue(reserved.isEmpty()) { reserved.toString() }
    }

    @Test
    fun `should generate valid demo names with an 8 character random suffix`() = runUnitTest {
        given()
        val count = 50

        whenn()
        val names = List(count) { BusinessLinkPolicy.randomDemoName() }

        then()
        assertTrue(
            names.all {
                BusinessLinkPolicy.isValid(it) && BusinessLinkPolicy.isDemoName(it)
            },
        ) { names.toString() }
        assertTrue(names.all { it.length == "demo-".length + 8 }) { names.toString() }
        assertEquals(count, names.toSet().size)
    }

    @Test
    fun `should not take a regular name for a demo name`() = runUnitTest {
        given()
        val name = "demon-tattoo"

        whenn()
        val demo = BusinessLinkPolicy.isDemoName(name)

        then()
        assertFalse(demo)
    }
}
