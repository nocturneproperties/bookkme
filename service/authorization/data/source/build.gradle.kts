plugins {
    alias(libs.plugins.bookk.domain.api)
}

dependencies {
    implementation(projects.core)
    implementation(projects.core.domain)
    implementation(projects.service.authorization.domain.api)
    implementation(libs.passkey)
}