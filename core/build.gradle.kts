import com.bookk.build_src.tools.libs

plugins {
    alias(libs.plugins.bookk.kotlin)
    id(libs.plugins.kotlin.fixtures.get().pluginId)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    api(libs.ktor.logging)
    api(libs.kotlin.coroutines)
    testImplementation(libs.kotlin.test)
    testFixturesApi(libs.kotlin.coroutines.test)
}
