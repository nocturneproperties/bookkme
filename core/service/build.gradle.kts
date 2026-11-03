import com.bookk.build_src.tools.libs

plugins {
    alias(libs.plugins.bookk.microservice)
}

group = "com.bookk.core.service"

application {
    mainClass.set("com.bookk.core.MockMainClassKt")
}

dependencies {
    implementation(libs.ktor.json)
    implementation(libs.ktor.logging)
    implementation(libs.ktor.status)
    implementation(libs.ktor.logging.call)
    implementation(libs.ktor.metrics)
    implementation(libs.ktor.swagger)
    implementation(libs.ktor.metrics.prometheus)
    implementation(projects.core)
    implementation(projects.core.data)
    implementation(projects.core.data.cache.api)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core.domain)
    implementation(projects.core.domain.datasource)
    implementation(projects.library.idempotency)
    implementation(projects.library.signing.api)
    implementation(projects.library.signing.impl)
    api(projects.service.authorization.client)
    testFixturesApi(testFixtures(projects.core))
}
