plugins {
    alias(libs.plugins.bookk.kotlin)
    alias(libs.plugins.kotlin.fixtures)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    api(libs.kotlin.coroutines)
    api(libs.ktor.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(testFixtures(projects.core))
    testImplementation(libs.kotlin.coroutines.test)
}
