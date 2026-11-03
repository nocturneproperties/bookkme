plugins {
    alias(libs.plugins.bookk.domain.api)
}

dependencies {
    api(projects.library.export.api)
    implementation(libs.ktor.core)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.service))
    testImplementation(libs.ktor.test)
    testImplementation(libs.koin.ktor)
    testImplementation(platform(libs.koin.bom))
}
