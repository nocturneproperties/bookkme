package library.encryption.impl

import com.bookk.core.data.DataSource
import library.encryption.impl.orm.SubjectKeyTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

internal class SubjectKeyDataSource(private val table: SubjectKeyTable) : DataSource() {

    suspend fun get(subjectId: Uuid): StoredSubjectKey? {
        return dbQuery {
            table.selectAll().where { table.subjectId eq subjectId }.singleOrNull()?.toStored()
        }
    }

    suspend fun getAll(subjectIds: Set<Uuid>): List<StoredSubjectKey> {
        return dbQuery {
            table.selectAll().where { table.subjectId inList subjectIds }.map { it.toStored() }
        }
    }

    suspend fun insertIfAbsent(key: StoredSubjectKey): StoredSubjectKey {
        return dbQuery {
            table.insertIgnore {
                it[subjectId] = key.subjectId
                it[wrappedKey] = key.wrappedKey
                it[masterKeyVersion] = key.masterKeyVersion
                it[createdAt] = key.createdAt
            }
            table.selectAll().where { table.subjectId eq key.subjectId }.forUpdate().single().toStored()
        }
    }

    suspend fun delete(subjectId: Uuid) {
        dbQuery {
            table.deleteWhere { table.subjectId eq subjectId }
        }
    }

    private fun ResultRow.toStored(): StoredSubjectKey {
        return StoredSubjectKey(
            subjectId = this[table.subjectId],
            wrappedKey = this[table.wrappedKey],
            masterKeyVersion = this[table.masterKeyVersion],
            createdAt = this[table.createdAt],
        )
    }
}
