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

    testFixturesImplementation(libs.h2)
    testFixturesImplementation(libs.mockk)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.data.cache.impl))
    testImplementation(libs.testcontainers.junit)
}
