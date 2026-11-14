plugins {
    alias(libs.plugins.bookk.data)
    alias(libs.plugins.kotlin.fixtures)
}

dependencies {
    api(projects.library.encryption.api)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(libs.micrometer.core)

    testFixturesApi(testFixtures(projects.core))
    testFixturesApi(libs.testcontainers.junit)
    testFixturesApi(libs.kotlin.test.junit5)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.data))
    testImplementation(libs.testcontainers.junit)
}
