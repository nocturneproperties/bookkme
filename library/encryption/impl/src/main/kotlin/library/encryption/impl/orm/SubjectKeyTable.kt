package library.encryption.impl.orm

import library.encryption.impl.StoredSubjectKey
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

internal class SubjectKeyTable(schema: String) : Table("$schema.subject_key") {
    val subjectId = uuid("subject_id")
    val wrappedKey = binary("wrapped_key", StoredSubjectKey.WRAPPED_KEY_LENGTH).nullable()
    val masterKeyVersion = integer("master_key_version").nullable()
    val createdAt = timestamp("created_at")
    val shreddedAt = timestamp("shredded_at").nullable()

    override val primaryKey = PrimaryKey(subjectId)
}
