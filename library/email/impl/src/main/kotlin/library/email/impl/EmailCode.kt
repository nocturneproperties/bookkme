package library.email.impl

import java.util.Random

internal fun newEmailCode(random: Random): String = buildString {
    repeat(EmailCodeIssuerImpl.CODE_LENGTH) {
        append(random.nextInt(10))
    }
}
