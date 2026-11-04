plugins {
    alias(libs.plugins.bookk.domain.api)
}

dependencies {
    implementation(libs.ktor.http)
    api(libs.kotlin.datetime)
    implementation(projects.core)
    api(projects.core.domain)
    api(projects.library.money)
    api(projects.library.schedule)
    api(projects.library.permissions)
    testImplementation(testFixtures(projects.core))
}
