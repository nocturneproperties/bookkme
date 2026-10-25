plugins {
    alias(libs.plugins.bookk.domain.impl)
}

dependencies {
    implementation(libs.ktor.jwt)
    implementation(libs.passkey)
    implementation(projects.core)
    implementation(projects.core.domain)
    implementation(projects.core.domain.datasource)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.service.authorization.domain.api)
    implementation(projects.library.signing.api)
    implementation(projects.library.signing.impl)
    implementation(projects.library.validation)
    implementation(projects.service.authorization.data.source)
    implementation(projects.service.authorization.client)
    implementation(projects.service.user.client)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.domain.datasource))
    testImplementation(libs.kotlin.coroutines.test)
}