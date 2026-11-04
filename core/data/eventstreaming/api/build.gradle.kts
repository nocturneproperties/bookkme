plugins {
    alias(libs.plugins.bookk.domain.api)
}

dependencies {
    api(libs.kotlin.coroutines)
    api(libs.ktor.core)
    api(platform(libs.koin.bom))
    api(libs.koin.core)
}
