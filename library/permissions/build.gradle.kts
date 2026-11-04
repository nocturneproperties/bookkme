plugins {
    alias(libs.plugins.bookk.domain.api)
}

dependencies {
    implementation(libs.ktor.http)
    api(projects.core.domain)
    testImplementation(testFixtures(projects.core))
}
