package library.encryption.impl.orm

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

internal object ShreddedSubjectTable : Table("shredded_subject") {
    val subjectId = uuid("subject_id")
    val shreddedAt = timestamp("shredded_at")

    override val primaryKey = PrimaryKey(subjectId)
}
