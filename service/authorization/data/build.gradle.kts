plugins {
    alias(libs.plugins.bookk.data)
}

dependencies {
    implementation(projects.core)
    implementation(projects.core.domain.datasource)
    implementation(projects.core.data)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core.data.eventstreaming.data)
    implementation(projects.core.data.cache.api)
    implementation(projects.library.signing.impl)
    implementation(projects.service.authorization.domain.api)
    implementation(projects.service.authorization.data.source)
    implementation(libs.passkey)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.data))
    testImplementation(testFixtures(projects.core.data.cache.impl))
}