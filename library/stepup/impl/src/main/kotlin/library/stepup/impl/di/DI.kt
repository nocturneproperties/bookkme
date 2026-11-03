package library.stepup.impl.di

import library.stepup.StepUpGate
import library.stepup.impl.StepUpGateImpl
import library.stepup.impl.StepUpTokenStore
import library.stepup.impl.StepUpVerifier
import org.koin.core.qualifier.Qualifier
import org.koin.dsl.module

fun stepUpModule(qualifier: Qualifier) = module {
    scope(qualifier) {
        scoped<StepUpGate> { StepUpGateImpl(StepUpVerifier(get()), StepUpTokenStore(get())) }
    }
}
