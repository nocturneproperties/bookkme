plugins {
    alias(libs.plugins.bookk.domain.impl)
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kotlin.fixtures.get().pluginId)
}

dependencies {
    implementation(libs.kotlin.coroutines)
    implementation(libs.ktor.protobuf)
    implementation(libs.kafka.client)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core)
    implementation(projects.core.domain)

    testFixturesApi(projects.core.data.eventstreaming.api)
    testFixturesApi(libs.kafka.client)
    testFixturesApi(libs.ktor.protobuf)
    testFixturesApi(libs.testcontainers.kafka)
    testFixturesImplementation(projects.core.data)
    testFixturesImplementation(projects.core.data.cache.api)
    testFixturesImplementation(testFixtures(projects.core.data.cache.impl))

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.kotlin.coroutines.test)
}
