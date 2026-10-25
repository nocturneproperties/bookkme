plugins {
    alias(libs.plugins.bookk.data)
    alias(libs.plugins.kotlin.fixtures)
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core)
    implementation(projects.core.domain.datasource)
    implementation(projects.core.data.cache.api)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.library.idempotency)

    testFixturesImplementation(libs.exposed.core)
    testFixturesImplementation(libs.exposed.jdbc)
    testFixturesImplementation(libs.h2)
    testFixturesImplementation(libs.kotlin.coroutines)
    testFixturesImplementation(libs.mockk)

    testImplementation(testFixtures(projects.core))
    testImplementation(projects.core.data.cache.api)
    testImplementation(testFixtures(projects.core.data.cache.impl))
    testImplementation(libs.testcontainers.junit)
}