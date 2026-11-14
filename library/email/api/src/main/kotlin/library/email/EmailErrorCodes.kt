package library.email

object EmailErrorCodes {
    private const val BASE = 910000

    const val EMAIL_CODE_INVALID = BASE + 1
    const val EMAIL_CODE_EXPIRED = BASE + 2
    const val EMAIL_CODE_ATTEMPTS_EXCEEDED = BASE + 3
    const val EMAIL_CODE_RESEND_TOO_SOON = BASE + 4
    const val EMAIL_UNDELIVERABLE = BASE + 5
}
