plugins {
    alias(libs.plugins.bookk.domain.api)
}

dependencies {
    implementation(libs.kotlin.coroutines)
    implementation(libs.ktor.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
}