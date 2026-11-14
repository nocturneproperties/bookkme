package library.encryption.impl

import io.ktor.util.logging.KtorSimpleLogger
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import library.encryption.MissingKeys

internal class MeteredMissingKeys(private val meterRegistry: MeterRegistry) : MissingKeys {

    private val logger = KtorSimpleLogger("MissingKeys")

    override fun report(source: String) {
        logger.error("Personal data in {} is unreadable: its subject's key is missing although never erased", source)
        Counter.builder(METRIC)
            .description("Personal data left unreadable because its data subject's key is missing")
            .tag(SOURCE_TAG, source)
            .register(meterRegistry)
            .increment()
    }

    companion object {
        const val METRIC = "personal_data.key_missing"
        const val SOURCE_TAG = "source"
    }
}
