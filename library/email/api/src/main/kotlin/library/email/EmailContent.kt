package library.email

data class EmailContent(
    val subject: String,
    val textBody: String,
    val htmlBody: String,
) {
    companion object {
        fun stub(
            subject: String = "Subject",
            textBody: String = "Text",
            htmlBody: String = "<p>Text</p>",
        ): EmailContent {
            return EmailContent(subject, textBody, htmlBody)
        }
    }
}
