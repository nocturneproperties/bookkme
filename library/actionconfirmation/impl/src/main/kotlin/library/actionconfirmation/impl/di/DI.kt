package library.actionconfirmation.impl.di

import library.actionconfirmation.ActionConfirmationGate
import library.actionconfirmation.impl.ActionConfirmationGateImpl
import library.actionconfirmation.impl.ActionConfirmationTokenStore
import library.actionconfirmation.impl.ActionConfirmationVerifier
import org.koin.core.qualifier.Qualifier
import org.koin.dsl.module

fun actionConfirmationModule(qualifier: Qualifier) = module {
    scope(qualifier) {
        scoped<ActionConfirmationGate> { ActionConfirmationGateImpl(ActionConfirmationVerifier(get()), ActionConfirmationTokenStore(get())) }
    }
}
