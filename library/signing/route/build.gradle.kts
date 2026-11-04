plugins {
    alias(libs.plugins.bookk.domain.api)
}

dependencies {
    implementation(projects.library.signing.api)
    implementation(projects.core.service)
    api(libs.ktor.core)
    implementation(libs.kotlin.serialization.json)
    api(libs.koin.core)
    api(platform(libs.koin.bom))
}
