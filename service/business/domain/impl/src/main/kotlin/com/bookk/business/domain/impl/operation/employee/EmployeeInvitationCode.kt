package com.bookk.business.domain.impl.operation.employee

import java.security.MessageDigest
import java.security.SecureRandom

internal object EmployeeInvitationCode {
    private const val LENGTH = 8
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private val secureRandom = SecureRandom()

    fun generate(): String = buildString {
        repeat(LENGTH) {
            append(ALPHABET[secureRandom.nextInt(ALPHABET.length)])
        }
    }

    fun hash(code: String): String =
        MessageDigest.getInstance("SHA-256").digest(code.toByteArray()).toHexString()
}
