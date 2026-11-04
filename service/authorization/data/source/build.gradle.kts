plugins {
    alias(libs.plugins.bookk.kotlin)
}

dependencies {
    api(projects.core.domain)
    api(projects.service.authorization.domain.api)
    api(libs.passkey)
}
