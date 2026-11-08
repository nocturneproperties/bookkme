package com.bookk.user.domain.api.error

object UserErrorCodes {
    private const val BASE = 100000

    const val USER_NOT_EXIST = BASE + 1
    const val USER_CONTACT_TAKEN = BASE + 2
    const val LEGAL_DOCUMENT_VERSION_UNKNOWN = BASE + 3
    const val LEGAL_DOCUMENTS_NOT_ACCEPTED = BASE + 4
    const val USER_ACTION_CONFIRMATION_INVALID = BASE + 5
    const val EMAIL_CHANGE_REQUIRES_VERIFICATION = BASE + 6
}
