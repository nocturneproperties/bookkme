plugins {
    alias(libs.plugins.bookk.domain.impl)
    alias(libs.plugins.kotlin.fixtures)
}

dependencies {
    api(libs.kotlin.serialization.protobuf)
    api(projects.core.data.cache.api)
    api(libs.redis.client)
    implementation(libs.redis.pool)
    runtimeOnly(libs.kotlin.coroutines.reactive)
    implementation(projects.core)
    implementation(projects.core.domain)

    testFixturesImplementation(libs.memcache)

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.testcontainers.junit)
}
