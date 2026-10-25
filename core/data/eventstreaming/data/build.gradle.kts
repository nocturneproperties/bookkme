plugins {
    alias(libs.plugins.bookk.data)
    id(libs.plugins.kotlin.fixtures.get().pluginId)
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core.domain.datasource)

    testFixturesApi(platform(libs.koin.bom))
    testFixturesApi(libs.koin.core)
    testFixturesImplementation(libs.mockk)
    testFixturesImplementation(projects.core.data.cache.api)
    testFixturesImplementation(projects.core.data.eventstreaming.api)
    testFixturesImplementation(projects.core.domain.datasource)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.data))
    testImplementation(testFixtures(projects.core.domain.datasource))
    testImplementation(libs.testcontainers.junit)
}
