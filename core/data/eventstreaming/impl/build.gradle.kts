plugins {
    alias(libs.plugins.bookk.domain.impl)
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kotlin.fixtures.get().pluginId)
}

dependencies {
    api(libs.kafka.client)
    api(libs.kotlin.serialization.protobuf)
    api(projects.core.data.eventstreaming.api)
    api(projects.core.domain.datasource)
    api(libs.micrometer.core)
    implementation(projects.core)
    implementation(projects.core.domain)

    testFixturesApi(libs.testcontainers.kafka)
    testFixturesImplementation(projects.core.data)
    testFixturesImplementation(testFixtures(projects.core.data.cache.impl))

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.domain.datasource))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.kotlin.coroutines.test)
}
