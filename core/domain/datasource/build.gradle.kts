plugins {
    alias(libs.plugins.bookk.kotlin)
    alias(libs.plugins.kotlin.fixtures)
}

dependencies {
    implementation(libs.kotlin.coroutines)
    testFixturesApi(libs.mockk)
}