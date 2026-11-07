plugins {
    alias(libs.plugins.bookk.data)
    alias(libs.plugins.kotlin.fixtures)
}

dependencies {
    implementation(libs.hikari.cp)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.mysql)
    runtimeOnly(libs.mariadb.jdbc)
    api(projects.core.domain)
    implementation(projects.core)
    api(projects.core.domain.datasource)
    api(projects.core.data.cache.api)
    api(projects.core.data.eventstreaming.api)
    api(projects.library.idempotency)

    testFixturesRuntimeOnly(libs.h2)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.data.cache.impl))
    testImplementation(libs.testcontainers.junit)
}
