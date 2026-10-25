plugins {
    alias(libs.plugins.bookk.domain.api)
    alias(libs.plugins.kotlin.fixtures)
}

dependencies {
    implementation(projects.core)
    implementation(libs.ktor.core)
    testImplementation(testFixtures(projects.core))
    testImplementation(libs.kotlin.coroutines.test)
}
