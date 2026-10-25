package library.signing.impl.di

import library.signing.GetActiveSigningKey
import library.signing.GetVerificationKeys
import library.signing.RotateSigningKeys
import library.signing.TokenIssuer
import library.signing.TokenValidatorFactory
import library.signing.impl.GetActiveSigningKeyImpl
import library.signing.impl.GetVerificationKeysImpl
import library.signing.impl.RotateSigningKeysImpl
import library.signing.impl.SigningKeyDataSource
import library.signing.impl.TokenValidatorFactoryImpl
import library.signing.impl.key.RsaSigningKeyFactory
import library.signing.impl.key.SigningKeyPairGenerator
import library.signing.impl.key.TokenIssuerImpl
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.Qualifier
import org.koin.dsl.bind
import org.koin.dsl.module

fun signingModule(qualifier: Qualifier) = module {
    scope(qualifier) {
        scopedOf(::SigningKeyDataSource)
        scoped { SigningKeyPairGenerator(RsaSigningKeyFactory::generate) }
        scopedOf(::GetActiveSigningKeyImpl) bind GetActiveSigningKey::class
        scopedOf(::GetVerificationKeysImpl) bind GetVerificationKeys::class
        scopedOf(::RotateSigningKeysImpl) bind RotateSigningKeys::class
        scopedOf(::TokenIssuerImpl) bind TokenIssuer::class
    }
}

fun tokenValidatorModule() = module {
    singleOf(::TokenValidatorFactoryImpl) bind TokenValidatorFactory::class
}
