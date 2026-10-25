package library.signing.impl.orm.table

import library.signing.SigningKeyStatus
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

internal object SigningKeyTable : UuidTable("signing_key") {
    val publicKey = text("public_key")
    val privateKey = text("private_key")
    val status = enumeration("status", SigningKeyStatus::class).index()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val retiredAt = timestamp("retired_at").nullable()
}
