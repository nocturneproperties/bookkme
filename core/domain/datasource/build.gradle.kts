plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.fixtures)
}

dependencies {
    implementation(libs.kotlin.coroutines)
    testFixturesApi(libs.mockk)
}