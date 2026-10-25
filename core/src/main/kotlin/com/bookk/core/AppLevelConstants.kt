package com.bookk.core

import java.io.File

object AppLevelConstants {
    const val APP_NAME = "BookkMe"
    const val SERIALIZER = SupportedSerializers.PROTOBUF.STR

    val domainName: String
        get() = System.getenv("APPLICATION_DOMAIN_NAME").orEmpty()
    val serviceName: String
        get() = System.getenv("APPLICATION_SERVICE_NAME").orEmpty()
    val serviceHostName: String
        get() = System.getenv("APPLICATION_SERVICE_HOSTNAME").orEmpty()
    val serviceVersion: String
        get() = System.getenv("APPLICATION_SERVICE_VERSION").orEmpty()
    val cacheHost: String
        get() = System.getenv("APPLICATION_REDIS_HOSTS")
    val cachePort: String
        get() = System.getenv("APPLICATION_REDIS_PORT")
    val cachePass: String
        get() = System.getenv("APPLICATION_REDIS_PASSWORD")
    val eventStreamingHost: String
        get() = System.getenv("APPLICATION_KAFKA_HOSTS")
    val servicePort: Int
        get() = System.getenv("APPLICATION_SERVICE_PORT").toInt()
    val dbUrl: String
        get() = System.getenv("APPLICATION_DB_URL")
    val dbPort: String
        get() = System.getenv("APPLICATION_DB_PORT")
    val dbUsername: String
        get() = System.getenv("APPLICATION_DB_USER")
    val dbPassword: String
        get() = System.getenv("APPLICATION_DB_PASSWORD")
    val dbPoolSize: Int
        get() = System.getenv("APPLICATION_DB_POOL_SIZE")?.toIntOrNull() ?: 10
    val signingKeyEncryptionKey: String
        get() = readSecret("signing_key_encryption_key")
    val firebasePrivateKey: String
        get() = readSecret("firebase_key")

    private fun readSecret(name: String): String {
        val envVarName = "APPLICATION_${name.uppercase()}_FILE"
        val path = System.getenv(envVarName) ?: "/run/secrets/$name"
        return File(path).takeIf { it.exists() }?.readText()?.trim().orEmpty()
    }

    sealed interface SupportedSerializers {
        data object JSON : SupportedSerializers {
            const val STR = "JSON"
        }

        data object PROTOBUF : SupportedSerializers {
            const val STR = "PROTOBUF"
        }
    }
}