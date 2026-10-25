plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    implementation(libs.kotlin.coroutines)
    implementation(libs.ktor.protobuf)
    testImplementation(libs.kotlin.test)
    testImplementation(testFixtures(projects.core))
}