plugins {
    alias(libs.plugins.bookk.data)
}

dependencies {
    implementation(libs.auth0.jwks)
    implementation(projects.core)
    implementation(projects.core.domain.datasource)
    implementation(projects.core.data)
    implementation(projects.library.signing.api)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.domain.datasource))
}
