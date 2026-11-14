package library.encryption.impl.di

import library.encryption.BlindIndex
import library.encryption.MissingKeys
import library.encryption.SubjectKeys
import library.encryption.impl.HmacBlindIndex
import library.encryption.impl.MasterKey
import library.encryption.impl.MeteredMissingKeys
import library.encryption.impl.SubjectKeyDataSource
import library.encryption.impl.SubjectKeysImpl
import library.encryption.impl.orm.SubjectKeyTable
import org.koin.dsl.ScopeDSL
import java.security.SecureRandom
import kotlin.time.Clock

private const val MASTER_KEY = "APPLICATION_SUBJECT_MASTER_KEY"
private const val BLIND_INDEX_KEY = "APPLICATION_BLIND_INDEX_KEY"
private const val MASTER_KEY_VERSION = 1

fun ScopeDSL.subjectKeys(keySchema: String, environment: (String) -> String? = System::getenv) {
    scoped { SubjectKeyDataSource(SubjectKeyTable(keySchema)) }
    scoped<SubjectKeys> {
        val masterKey = environment.parse(MASTER_KEY) { MasterKey.fromBase64(MASTER_KEY_VERSION, it) }
        SubjectKeysImpl(get(), masterKey, SecureRandom(), Clock.System)
    }
    scoped<BlindIndex> { environment.parse(BLIND_INDEX_KEY, HmacBlindIndex::fromBase64) }
    scoped<MissingKeys> { MeteredMissingKeys(get()) }
}

private fun <T> ((String) -> String?).parse(variable: String, parse: (String) -> T): T {
    val value = this(variable)
        ?: throw IllegalStateException("$variable is not set, so personal data can't be encrypted")
    return runCatching { parse(value) }.getOrElse { cause ->
        throw IllegalStateException("$variable is not a valid key", cause)
    }
}
