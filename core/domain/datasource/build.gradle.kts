plugins {
    alias(libs.plugins.bookk.kotlin)
    alias(libs.plugins.kotlin.fixtures)
}

dependencies {
    testFixturesApi(libs.mockk)
}
