plugins {
    alias(libs.plugins.bookk.data)
}

dependencies {
    implementation(projects.core)
    implementation(projects.core.domain)
    implementation(projects.core.domain.datasource)
    implementation(projects.core.data)
    implementation(libs.ktor.auth)
    implementation(libs.ktor.jwt)
    implementation(projects.library.signing.api)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.domain.datasource))
    testImplementation(libs.kotlin.coroutines.test)
}
