package com.bookk.user.domain.api.operation

import com.bookk.user.domain.api.entity.ContactForm

interface CreateContactForm {
    suspend operator fun invoke(form: ContactForm): Result<Unit>
}