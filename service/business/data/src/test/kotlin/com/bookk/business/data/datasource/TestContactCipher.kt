package com.bookk.business.data.datasource

import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys
import library.encryption.test.UnkeyedBlindIndex

internal fun testContactCipher(): ContactCipher {
    return ContactCipher(InMemorySubjectKeys(), UnkeyedBlindIndex(), RecordingMissingKeys())
}
