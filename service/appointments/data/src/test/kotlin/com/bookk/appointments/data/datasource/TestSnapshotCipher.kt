package com.bookk.appointments.data.datasource

import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys
import library.encryption.test.UnkeyedBlindIndex

internal fun testSnapshotCipher(): SnapshotCipher {
    return SnapshotCipher(InMemorySubjectKeys(), UnkeyedBlindIndex(), RecordingMissingKeys())
}
