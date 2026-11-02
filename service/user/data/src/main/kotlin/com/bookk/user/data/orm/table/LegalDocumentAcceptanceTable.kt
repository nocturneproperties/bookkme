package com.bookk.user.data.orm.table

import com.bookk.user.domain.api.entity.LegalDocument
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object LegalDocumentAcceptanceTable : Table("legal_document_acceptance") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val document = enumerationByName<LegalDocument>("document", 32)
    val version = varchar("version", 32)
    val acceptedAt = timestamp("accepted_at")

    override val primaryKey = PrimaryKey(userId, document, version)

    init {
        index(isUnique = false, document, version)
        foreignKey(
            document to LegalDocumentVersionTable.document,
            version to LegalDocumentVersionTable.version,
            onUpdate = ReferenceOption.RESTRICT,
            onDelete = ReferenceOption.RESTRICT,
            name = "fk_legal_document_acceptance_version",
        )
    }
}
