package com.bookk.auth.domain.impl.email

import com.bookk.core.domain.entity.Language
import com.bookk.core.i18n.LocalizedStrings
import library.email.EmailContent

internal object AuthEmails {
    private val strings = LocalizedStrings("i18n.auth_emails")

    fun signUpCode(language: Language, code: String): EmailContent {
        return codeEmail(language, "sign_up", code, tag = "sign-up-code")
    }

    fun emailChangeCode(language: Language, code: String): EmailContent {
        return codeEmail(language, "email_change", code, tag = "email-change-code")
    }

    private fun codeEmail(language: Language, kind: String, code: String, tag: String): EmailContent {
        val intro = strings.message(language, "$kind.body")
        val notYou = strings.message(language, "$kind.not_you")
        return EmailContent(
            subject = strings.message(language, "$kind.subject"),
            textBody = "$intro\n\n$code\n\n$notYou",
            htmlBody = "<p>$intro</p>" +
                "<p style=\"font-size:28px;font-weight:bold;letter-spacing:6px\">$code</p>" +
                "<p>$notYou</p>",
            tag = tag,
        )
    }
}
