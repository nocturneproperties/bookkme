package library.email

data class EmailMessage(
    val to: String,
    val content: EmailContent,
) {
    companion object {
        fun stub(
            to: String = "client@example.com",
            content: EmailContent = EmailContent.stub(),
        ) = EmailMessage(to, content)
    }
}
