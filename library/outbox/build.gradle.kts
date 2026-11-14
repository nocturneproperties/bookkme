plugins {
    alias(libs.plugins.bookk.data)
    id(libs.plugins.kotlin.fixtures.get().pluginId)
}

dependencies {
    api(projects.core.data)
    api(projects.core.data.eventstreaming.api)
    api(projects.core.domain.datasource)
    api(projects.library.encryption.api)
    implementation(libs.micrometer.core)

    testFixturesImplementation(libs.mockk)
    testFixturesImplementation(libs.micrometer.core)
    testFixturesImplementation(projects.core.data.cache.api)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.data))
    testImplementation(testFixtures(projects.core.domain.datasource))
    testImplementation(testFixtures(projects.core.data.eventstreaming.impl))
    testImplementation(libs.testcontainers.junit)
}
