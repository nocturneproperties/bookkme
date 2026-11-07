plugins {
    alias(libs.plugins.bookk.data)
}

dependencies {
    implementation(projects.core)
    implementation(projects.core.domain.datasource)
    api(projects.core.data)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core.data.eventstreaming.data)
    implementation(projects.core.data.cache.api)
    api(projects.service.authorization.domain.api)
    implementation(projects.service.authorization.data.source)
    implementation(libs.passkey)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.data))
    testImplementation(libs.testcontainers.junit)
    testImplementation(testFixtures(projects.core.data.cache.impl))
}
