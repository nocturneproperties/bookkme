plugins {
    alias(libs.plugins.bookk.domain.api)
}

dependencies {
    implementation(projects.core)
    implementation(projects.core.domain)
    implementation(libs.ktor.auth)
    implementation(libs.ktor.jwt)
    api(libs.auth0.jwt)
}
