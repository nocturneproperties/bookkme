plugins {
    alias(libs.plugins.bookk.kotlin)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.fixtures)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    implementation(libs.kotlin.coroutines)
    api(libs.kotlin.serialization.protobuf)
    testImplementation(libs.kotlin.test)
    testImplementation(testFixtures(projects.core))
}
