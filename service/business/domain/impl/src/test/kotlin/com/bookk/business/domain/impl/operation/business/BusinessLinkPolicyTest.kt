package com.bookk.business.domain.impl.operation.business

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
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
}
