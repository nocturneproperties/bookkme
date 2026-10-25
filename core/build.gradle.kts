import com.bookk.build_src.tools.libs

plugins {
    alias(libs.plugins.kotlin.jvm)
    id(libs.plugins.kotlin.fixtures.get().pluginId)
}

dependencies {
    api(libs.kotlin.datetime)
    api(libs.ktor.logging)
    implementation(libs.kotlin.coroutines)
    testImplementation(libs.kotlin.test)
    testFixturesImplementation(libs.mockk)
    testFixturesApi(libs.kotlin.coroutines.test)
}