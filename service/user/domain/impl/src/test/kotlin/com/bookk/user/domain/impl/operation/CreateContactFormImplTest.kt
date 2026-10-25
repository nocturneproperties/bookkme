package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.api.entity.ContactForm
import com.bookk.user.domain.datasource.CommunicationDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class CreateContactFormImplTest {

    private class SutFixture {
        val communicationDataSource = mockk<CommunicationDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = CreateContactFormImpl(communicationDataSource, transactionManager)
    }

    private fun makeForm(
        text: String = "Hello",
        usageLogs: String? = null
    ): ContactForm = ContactForm(
        userId = Uuid.random(),
        text = text,
        usageLogs = usageLogs,
        status = ContactForm.ContactFormStatus.NEW
    )

    @Test
    fun `should save form as-is when text and logs are within bounds`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val form = makeForm(text = "Short message", usageLogs = "Short logs")
        val savedSlot = slot<ContactForm>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { communicationDataSource.saveContactForm(capture(savedSlot)) } returns Unit
        }

        whenn()
        val result = fixture.sut.invoke(form)

        then()
        assertTrue(result.isSuccess)
        assertEquals(form.text, savedSlot.captured.text)
        assertEquals(form.usageLogs, savedSlot.captured.usageLogs)
    }

    @Test
    fun `should truncate text to upper bound when it exceeds UShort max value`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val oversizedText = "x".repeat(ContactForm.TEXT_UPPER_BOUND.toInt() + 100)
        val form = makeForm(text = oversizedText)
        val savedSlot = slot<ContactForm>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { communicationDataSource.saveContactForm(capture(savedSlot)) } returns Unit
        }

        whenn()
        val result = fixture.sut.invoke(form)

        then()
        assertTrue(result.isSuccess)
        assertEquals(ContactForm.TEXT_UPPER_BOUND.toInt(), savedSlot.captured.text.length)
    }

    @Test
    fun `should truncate logs to upper bound when they exceed UShort max value`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val oversizedLogs = "l".repeat(ContactForm.LOGS_UPPER_BOUND.toInt() + 100)
        val form = makeForm(usageLogs = oversizedLogs)
        val savedSlot = slot<ContactForm>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { communicationDataSource.saveContactForm(capture(savedSlot)) } returns Unit
        }

        whenn()
        val result = fixture.sut.invoke(form)

        then()
        assertTrue(result.isSuccess)
        assertEquals(ContactForm.LOGS_UPPER_BOUND.toInt(), savedSlot.captured.usageLogs?.length)
    }

    @Test
    fun `should handle null usage logs without truncation`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val form = makeForm(usageLogs = null)
        val savedSlot = slot<ContactForm>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { communicationDataSource.saveContactForm(capture(savedSlot)) } returns Unit
        }

        whenn()
        val result = fixture.sut.invoke(form)

        then()
        assertTrue(result.isSuccess)
        assertNull(savedSlot.captured.usageLogs)
        coVerify(exactly = 1) { fixture.communicationDataSource.saveContactForm(any()) }
    }
}
