plugins {
    alias(libs.plugins.bookk.domain.impl)
    alias(libs.plugins.kotlin.fixtures)
}

dependencies {
    api(libs.ktor.protobuf)
    api(projects.core.data.cache.api)
    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlin.coroutines.reactive)
    implementation(libs.redis.client)
    implementation(libs.redis.pool)
    implementation(projects.core)
    implementation(projects.core.domain)

    testFixturesImplementation(libs.memcache)

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.kotlin.coroutines.test)
}
