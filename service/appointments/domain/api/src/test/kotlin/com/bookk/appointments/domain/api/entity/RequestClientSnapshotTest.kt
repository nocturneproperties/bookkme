package com.bookk.appointments.domain.api.entity

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RequestClientSnapshotTest {

    @Test
    fun `should keep every field, the erased mark included, as a client snapshot`() = runUnitTest {
        given()
        val requestClient = RequestClientSnapshot.stub().copy(fullName = "", phone = null, email = null, erased = true)

        whenn()
        val client = requestClient.toClientSnapshot()

        then()
        assertEquals(
            ClientSnapshot(
                id = requestClient.id,
                fullName = "",
                phone = null,
                email = null,
                userId = requestClient.userId,
                erased = true,
            ),
            client,
        )
    }
}
