package library.encryption.impl

import com.bookk.core.data.DataSource
import library.encryption.impl.orm.SubjectKeyTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
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

    suspend fun insertIfAbsent(key: StoredSubjectKey.Live): StoredSubjectKey {
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

    suspend fun shred(subjectId: Uuid, at: Instant) {
        dbQuery {
            table.insertIgnore {
                it[table.subjectId] = subjectId
                it[createdAt] = at
                it[shreddedAt] = at
            }
            table.update({ (table.subjectId eq subjectId) and table.shreddedAt.isNull() }) {
                it[wrappedKey] = null
                it[masterKeyVersion] = null
                it[shreddedAt] = at
            }
        }
    }

    private fun ResultRow.toStored(): StoredSubjectKey {
        val subjectId = this[table.subjectId]
        val shreddedAt = this[table.shreddedAt]
        if (shreddedAt != null) return StoredSubjectKey.Shredded(subjectId, shreddedAt)
        return StoredSubjectKey.Live(
            subjectId = subjectId,
            wrappedKey = checkNotNull(this[table.wrappedKey]) {
                "Subject $subjectId has neither a key nor a tombstone"
            },
            masterKeyVersion = checkNotNull(this[table.masterKeyVersion]) {
                "Subject $subjectId has no master key version"
            },
            createdAt = this[table.createdAt],
        )
    }
}
