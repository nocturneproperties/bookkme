package library.email.impl

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

internal class EmailCodeMetrics(meterRegistry: MeterRegistry) {

    private val limitedAddresses = Counter.builder(SEND_LIMITED)
        .description("Addresses that reached the hourly email code send limit, counted once per address and hour")
        .register(meterRegistry)

    private val wrongCodes = rejected(meterRegistry, WRONG_CODE)

    private val exhaustedVerifications = rejected(meterRegistry, ATTEMPTS_EXHAUSTED)

    fun sendLimitReached() {
        limitedAddresses.increment()
    }

    fun wrongCode() {
        wrongCodes.increment()
    }

    fun attemptsExhausted() {
        exhaustedVerifications.increment()
    }

    private fun rejected(meterRegistry: MeterRegistry, reason: String): Counter {
        return Counter.builder(REJECTED)
            .description("Email codes refused: a wrong code, or a verification ended after too many wrong codes")
            .tag(REASON_TAG, reason)
            .register(meterRegistry)
    }

    companion object {
        const val SEND_LIMITED = "email_code.send_limited"
        const val REJECTED = "email_code.rejected"
        const val REASON_TAG = "reason"
        const val WRONG_CODE = "wrong_code"
        const val ATTEMPTS_EXHAUSTED = "attempts_exhausted"
    }
}
