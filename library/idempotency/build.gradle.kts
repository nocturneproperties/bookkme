plugins {
    alias(libs.plugins.bookk.domain.api)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.core)
    implementation(projects.core.domain)
    implementation(libs.ktor.core)
    implementation(libs.ktor.json)
}