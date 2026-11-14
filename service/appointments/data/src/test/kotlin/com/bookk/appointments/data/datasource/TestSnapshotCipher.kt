package com.bookk.appointments.data.datasource

import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys

internal fun testSnapshotCipher(): SnapshotCipher {
    return SnapshotCipher(InMemorySubjectKeys(), RecordingMissingKeys())
}
