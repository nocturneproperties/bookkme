package library.outbox.di

import library.outbox.personal.ClientKey
import library.outbox.personal.NoPersonalDataCipher
import library.outbox.personal.PersonalDataCipher
import library.outbox.personal.SubjectKeysPersonalDataCipher
import org.koin.dsl.ScopeDSL

fun ScopeDSL.personalDataCipher(clientKey: ClientKey = ClientKey.CLIENT_ID) {
    scoped<PersonalDataCipher> { SubjectKeysPersonalDataCipher(get(), clientKey, get()) }
}

fun ScopeDSL.noPersonalDataCipher() {
    scoped<PersonalDataCipher> { NoPersonalDataCipher }
}
