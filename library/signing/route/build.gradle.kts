plugins {
    alias(libs.plugins.bookk.domain.api)
}

dependencies {
    implementation(projects.library.signing.api)
    implementation(projects.core.service)
    implementation(libs.ktor.core)
    implementation(libs.ktor.json)
    implementation(libs.koin.ktor)
    implementation(platform(libs.koin.bom))
}
