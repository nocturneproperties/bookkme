package library.encryption.test

import library.encryption.MissingKeys

class RecordingMissingKeys : MissingKeys {
    private val sources = mutableListOf<String>()

    val reported: List<String>
        get() = sources.toList()

    override fun report(source: String) {
        sources += source
    }
}
