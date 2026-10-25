package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.user.domain.api.entity.ContactForm
import com.bookk.user.domain.api.operation.CreateContactForm
import com.bookk.user.domain.datasource.CommunicationDataSource

internal class CreateContactFormImpl(
    private val communicationDataSource: CommunicationDataSource,
    private val transactionManager: TransactionManager
) : CreateContactForm {
    override suspend fun invoke(form: ContactForm): Result<Unit> = transactionManager.transaction {
        val adjustedForm = if (form.isBoundCapRequired) {
            form.copy(
                text = form.text.take(ContactForm.TEXT_UPPER_BOUND.toInt()),
                usageLogs = form.usageLogs?.take(ContactForm.LOGS_UPPER_BOUND.toInt())
            )
        } else {
            form
        }
        communicationDataSource.saveContactForm(adjustedForm)
    }
}