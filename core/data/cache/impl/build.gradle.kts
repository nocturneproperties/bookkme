plugins {
    alias(libs.plugins.bookk.domain.impl)
    alias(libs.plugins.kotlin.fixtures)
}

dependencies {
    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlin.coroutines.reactive)
    implementation(libs.ktor.protobuf)
    implementation(libs.redis.client)
    implementation(libs.redis.pool)
    implementation(projects.core)
    implementation(projects.core.data.cache.api)

    testFixturesImplementation(projects.core.data.cache.api)
    testFixturesImplementation(libs.ktor.protobuf)
    testFixturesImplementation(libs.memcache)

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.kotlin.coroutines.test)
}