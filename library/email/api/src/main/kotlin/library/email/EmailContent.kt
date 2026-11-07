package library.email

data class EmailContent(
    val subject: String,
    val textBody: String,
    val htmlBody: String,
    val tag: String,
) {
    companion object {
        fun stub(
            subject: String = "Subject",
            textBody: String = "Text",
            htmlBody: String = "<p>Text</p>",
            tag: String = "tag",
        ) = EmailContent(subject, textBody, htmlBody, tag)
    }
}
