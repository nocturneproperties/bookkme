package com.bookk.user.domain.datasource

import com.bookk.user.domain.api.entity.ContactForm

interface CommunicationDataSource {
    suspend fun saveContactForm(form: ContactForm)
}