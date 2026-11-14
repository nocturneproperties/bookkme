package com.bookk.user.data.datasource

import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.data.map.toDomain
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.data.orm.table.UserTable
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.entity.UserEditModel
import library.encryption.Decrypted
import library.encryption.ofCaseInsensitive
import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys
import library.encryption.test.UnkeyedBlindIndex
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class UserDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(UserTable)
        val cacheClient = InMemoryCacheClient()
        val subjectKeys = InMemorySubjectKeys()
        val blindIndex = UnkeyedBlindIndex()
        val missingKeys = RecordingMissingKeys()
        val profileCipher = ProfileCipher(subjectKeys, blindIndex, missingKeys)
        val sut = UserDataSourceImpl(cacheClient, profileCipher)
        val uncached = UserDataSourceImpl(InMemoryCacheClient(), profileCipher)

        suspend fun storedRow(id: Uuid): ResultRow {
            return suspendTransaction { UserTable.selectAll().where { UserTable.id eq id }.single() }
        }

        suspend fun insertLegacy(user: User) {
            suspendTransaction {
                UserTable.insert {
                    it[UserTable.id] = user.id
                    it[name] = user.name
                    it[lastName] = user.lastName
                    it[email] = user.email
                    it[phone] = user.phone
                }
            }
        }

        suspend fun decrypted(id: Uuid, ciphertext: ByteArray?): String? {
            val decryptor = subjectKeys.decryptor(listOf(id))
            return ciphertext?.let { (decryptor.decrypt(id, it) as Decrypted.Plain).value.decodeToString() }
        }
    }

    private fun phoneEdit(phone: String?): UserEditModel {
        return UserEditModel(id = null, firstName = null, lastName = null, email = null, phone = phone)
    }

    private fun nameEdit(firstName: String): UserEditModel {
        return UserEditModel(id = null, firstName = firstName, lastName = null, email = null, phone = null)
    }

    @Test
    fun `should store the profile encrypted under the user's key and no plaintext`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub(name = "Alice", lastName = "Smith", email = "alice@example.com", phone = "+10000000000")

        whenn()
        suspendTransaction { fixture.sut.insertNewUser(user) }

        then()
        val row = fixture.storedRow(user.id)
        assertNull(row[UserTable.name])
        assertNull(row[UserTable.lastName])
        assertNull(row[UserTable.email])
        assertNull(row[UserTable.phone])
        assertEquals("Alice", fixture.decrypted(user.id, row[UserTable.nameEncrypted]))
        assertEquals("Smith", fixture.decrypted(user.id, row[UserTable.lastNameEncrypted]))
        assertEquals("alice@example.com", fixture.decrypted(user.id, row[UserTable.emailEncrypted]))
        assertEquals("+10000000000", fixture.decrypted(user.id, row[UserTable.phoneEncrypted]))
    }

    @Test
    fun `should index the email and phone ignoring case and surrounding spaces`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub(email = "Alice@Example.com", phone = " +10000000000 ")

        whenn()
        suspendTransaction { fixture.sut.insertNewUser(user) }

        then()
        val row = fixture.storedRow(user.id)
        assertEquals(fixture.blindIndex.of("alice@example.com"), row[UserTable.emailIndex])
        assertEquals(fixture.blindIndex.of("+10000000000"), row[UserTable.phoneIndex])
        assertEquals("Alice@Example.com", fixture.decrypted(user.id, row[UserTable.emailEncrypted]))
    }

    @Test
    fun `should find a user by email typed in another case`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub(email = "find-me@example.com")
        suspendTransaction { fixture.sut.insertNewUser(user) }

        whenn()
        val found = suspendTransaction { fixture.sut.getUserByEmail(" Find-Me@Example.COM") }

        then()
        assertEquals(user, found)
    }

    @Test
    fun `should reject a new user whose email differs from a stored one only by case`() = runUnitTest {
        given()
        val fixture = SutFixture()
        suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "taken@example.com")) }

        whenn()
        val result = runCatching {
            suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "Taken@Example.com")) }
        }

        then()
        assertTrue(result.exceptionOrNull()?.toDomain() is Error.UniqueConstraintFailed) { result.toString() }
    }

    @Test
    fun `should read a profile stored as plaintext before encryption`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub(name = "Legacy", email = "legacy@example.com", phone = "+10000000001")
        fixture.insertLegacy(user)

        whenn()
        val found = suspendTransaction { fixture.uncached.getUserById(user.id) }

        then()
        assertEquals(user, found)
    }

    @Test
    fun `should find a plaintext profile by its email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub(email = "legacy@example.com")
        fixture.insertLegacy(user)

        whenn()
        val found = suspendTransaction { fixture.sut.getUserByEmail("legacy@example.com") }

        then()
        assertEquals(user, found)
    }

    @Test
    fun `should reject a new user whose email a plaintext profile holds`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.insertLegacy(User.stub(email = "legacy@example.com"))

        whenn()
        val result = runCatching {
            suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "legacy@example.com")) }
        }

        then()
        assertTrue(result.exceptionOrNull()?.toDomain() is Error.UniqueConstraintFailed) { result.toString() }
    }

    @Test
    fun `should reject a phone a plaintext profile holds`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.insertLegacy(User.stub(email = "legacy@example.com", phone = "+19999999999"))
        val other = suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "other@example.com")) }

        whenn()
        val result = runCatching {
            suspendTransaction { fixture.sut.updateUser(other.id, phoneEdit("+19999999999"), Clock.System.now()) }
        }

        then()
        assertTrue(result.exceptionOrNull()?.toDomain() is Error.UniqueConstraintFailed) { result.toString() }
    }

    @Test
    fun `should reject a changed email a plaintext profile holds`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.insertLegacy(User.stub(email = "legacy@example.com"))
        val other = suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "other@example.com")) }

        whenn()
        val result = runCatching {
            suspendTransaction { fixture.sut.changeEmail(other.id, "legacy@example.com", Clock.System.now()) }
        }

        then()
        assertTrue(result.exceptionOrNull()?.toDomain() is Error.UniqueConstraintFailed) { result.toString() }
    }

    @Test
    fun `should encrypt a plaintext profile when it is edited`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub(name = "Legacy", email = "legacy@example.com", phone = "+10000000001")
        fixture.insertLegacy(user)

        whenn()
        val updated = suspendTransaction { fixture.sut.updateUser(user.id, nameEdit("Renamed"), Clock.System.now()) }

        then()
        val row = fixture.storedRow(user.id)
        assertEquals(user.copy(name = "Renamed"), updated)
        assertNull(row[UserTable.name])
        assertNull(row[UserTable.email])
        assertNull(row[UserTable.phone])
        assertEquals(fixture.blindIndex.of("legacy@example.com"), row[UserTable.emailIndex])
        assertEquals(fixture.blindIndex.of("+10000000001"), row[UserTable.phoneIndex])
        assertEquals("legacy@example.com", fixture.decrypted(user.id, row[UserTable.emailEncrypted]))
    }

    @Test
    fun `should read empty details and report the missing key when a profile's key was lost`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub(name = "Alice", email = "alice@example.com", phone = "+10000000000")
        suspendTransaction { fixture.sut.insertNewUser(user) }
        fixture.subjectKeys.loseKey(user.id)

        whenn()
        val found = suspendTransaction { fixture.uncached.getUserById(user.id) }

        then()
        assertEquals(User(id = user.id, name = "", lastName = "", email = "", phone = null), found)
        assertEquals(listOf("profile"), fixture.missingKeys.reported)
    }

    @Test
    fun `should keep a profile with a lost key findable by email after the user re-enters their name`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            val user = User.stub(name = "Alice", email = "alice@example.com")
            suspendTransaction { fixture.sut.insertNewUser(user) }
            fixture.subjectKeys.loseKey(user.id)

            whenn()
            suspendTransaction { fixture.uncached.updateUser(user.id, nameEdit("Alicia"), Clock.System.now()) }
            val found = suspendTransaction { fixture.uncached.getUserByEmail("alice@example.com") }

            then()
            assertEquals(user.id, found!!.id)
            assertEquals("Alicia", found.name)
        }

    @Test
    fun `should encrypt plaintext profiles in batches`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val legacy = List(3) { User.stub(email = "legacy$it@example.com", phone = "+1000000000$it") }
        legacy.forEach { fixture.insertLegacy(it) }

        whenn()
        val first = suspendTransaction { fixture.sut.encryptPlaintextProfiles(limit = 2) }
        val second = suspendTransaction { fixture.sut.encryptPlaintextProfiles(limit = 2) }
        val third = suspendTransaction { fixture.sut.encryptPlaintextProfiles(limit = 2) }

        then()
        assertEquals(listOf(2, 1, 0), listOf(first, second, third))
        legacy.forEach { user ->
            val row = fixture.storedRow(user.id)
            assertNull(row[UserTable.email])
            assertNull(row[UserTable.phone])
            assertEquals(fixture.blindIndex.ofCaseInsensitive(user.email), row[UserTable.emailIndex])
            assertEquals(user, suspendTransaction { fixture.uncached.getUserById(user.id) })
        }
    }

    @Test
    fun `should leave encrypted profiles alone when encrypting plaintext ones`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub(email = "sealed@example.com")
        suspendTransaction { fixture.sut.insertNewUser(user) }
        val before = fixture.storedRow(user.id)[UserTable.emailEncrypted]

        whenn()
        val encrypted = suspendTransaction { fixture.sut.encryptPlaintextProfiles(limit = 10) }

        then()
        assertEquals(0, encrypted)
        assertArrayEquals(before, fixture.storedRow(user.id)[UserTable.emailEncrypted])
    }

    @Test
    fun `should insert user and retrieve by id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub()

        whenn()
        val created = suspendTransaction { fixture.sut.insertNewUser(user) }
        val found = suspendTransaction { fixture.sut.getUserById(created.id) }

        then()
        assertNotNull(found)
        assertEquals("Alice", found!!.name)
        assertEquals(user.email, found.email)
    }

    @Test
    fun `should return null when user not found by id`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getUserById(Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should retrieve user by email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "find-me@example.com")) }

        whenn()
        val found = suspendTransaction { fixture.sut.getUserByEmail("find-me@example.com") }

        then()
        assertNotNull(found)
        assertEquals("Alice", found!!.name)
    }

    @Test
    fun `should return null when user not found by email`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getUserByEmail("nobody@example.com") }

        then()
        assertNull(found)
    }

    @Test
    fun `should update user name and return true`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub()) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateUser(
                created.id,
                UserEditModel(id = null, firstName = "Bob", lastName = null, email = null, phone = null),
                Clock.System.now(),
            )
        }
        val found = suspendTransaction { fixture.sut.getUserById(created.id) }

        then()
        assertNotNull(updated)
        assertEquals("Bob", found!!.name)
    }

    @Test
    fun `should round trip an optional phone`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub(phone = "+10000000000")) }
        val found = suspendTransaction { fixture.sut.getUserById(created.id) }

        then()
        assertEquals("+10000000000", found!!.phone)
    }

    @Test
    fun `should leave phone null when it is not supplied`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub()) }
        val found = suspendTransaction { fixture.sut.getUserById(created.id) }

        then()
        assertNull(found!!.phone)
    }

    @Test
    fun `should update phone without touching the other fields`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub(name = "Alice")) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateUser(
                created.id,
                UserEditModel(id = null, firstName = null, lastName = null, email = null, phone = "+19999999999"),
                Clock.System.now(),
            )
        }

        then()
        assertEquals("+19999999999", updated!!.phone)
        assertEquals("Alice", updated.name)
    }

    @Test
    fun `should store a blank phone as null on insert`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub(phone = " ")) }

        then()
        val found = suspendTransaction { fixture.sut.getUserById(created.id) }
        assertNull(found!!.phone)
    }

    @Test
    fun `should clear the phone when a blank one is supplied`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub(phone = "+19999999999")) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateUser(
                created.id,
                UserEditModel(id = null, firstName = null, lastName = null, email = null, phone = "  "),
                Clock.System.now(),
            )
        }

        then()
        assertNull(updated!!.phone)
    }

    @Test
    fun `should allow several users without a phone after clearing it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first =
            suspendTransaction {
                fixture.sut.insertNewUser(User.stub(email = "first@example.com", phone = "+11111111111"))
            }
        val second =
            suspendTransaction {
                fixture.sut.insertNewUser(User.stub(email = "second@example.com", phone = "+12222222222"))
            }
        val clearPhone = UserEditModel(id = null, firstName = null, lastName = null, email = null, phone = "")

        whenn()
        val result = runCatching {
            suspendTransaction {
                fixture.sut.updateUser(first.id, clearPhone, Clock.System.now())
                fixture.sut.updateUser(second.id, clearPhone, Clock.System.now())
            }
        }

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should reject a phone already used by another user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "owner@example.com", phone = "+19999999999")) }
        val other = suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "other@example.com")) }

        whenn()
        val result = runCatching {
            suspendTransaction {
                fixture.sut.updateUser(
                    other.id,
                    UserEditModel(id = null, firstName = null, lastName = null, email = null, phone = "+19999999999"),
                    Clock.System.now(),
                )
            }
        }

        then()
        assertTrue(result.exceptionOrNull()?.toDomain() is Error.UniqueConstraintFailed)
    }

    @Test
    fun `should return null when updating non-existent user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateUser(
                Uuid.random(),
                UserEditModel(id = null, firstName = "Bob", lastName = null, email = null, phone = null),
                Clock.System.now(),
            )
        }

        then()
        assertNull(updated)
    }

    @Test
    fun `should leave the email alone when an edit sends another one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "kept@example.com")) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateUser(
                created.id,
                UserEditModel(
                    id = null,
                    firstName = null,
                    lastName = null,
                    email = "changed@example.com",
                    phone = null,
                ),
                Clock.System.now(),
            )
        }
        val found = suspendTransaction { fixture.sut.getUserById(created.id) }

        then()
        assertEquals("kept@example.com", updated!!.email)
        assertEquals("kept@example.com", found!!.email)
    }

    @Test
    fun `should store a changed email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "old@example.com")) }
        val updatedAt = Instant.parse("2026-11-08T10:00:00Z")

        whenn()
        val changed = suspendTransaction { fixture.sut.changeEmail(created.id, "new@example.com", updatedAt) }
        val found = suspendTransaction { fixture.uncached.getUserById(created.id) }

        then()
        assertEquals(changed, found)
        assertEquals("new@example.com", found!!.email)
    }

    @Test
    fun `should stop serving the cached profile once the email changes`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "old@example.com")) }
        suspendTransaction { fixture.sut.getUserById(created.id) }

        whenn()
        suspendTransaction { fixture.sut.changeEmail(created.id, "new@example.com", Clock.System.now()) }
        val found = suspendTransaction { fixture.sut.getUserById(created.id) }

        then()
        assertEquals("new@example.com", found!!.email)
    }

    @Test
    fun `should reject an email another user already holds`() = runUnitTest {
        given()
        val fixture = SutFixture()
        suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "taken@example.com")) }
        val other = suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "other@example.com")) }

        whenn()
        val result = runCatching {
            suspendTransaction { fixture.sut.changeEmail(other.id, "taken@example.com", Clock.System.now()) }
        }

        then()
        assertTrue(result.exceptionOrNull()?.toDomain() is Error.UniqueConstraintFailed) { result.toString() }
    }

    @Test
    fun `should return null when changing the email of a missing user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val changed =
            suspendTransaction { fixture.sut.changeEmail(Uuid.random(), "new@example.com", Clock.System.now()) }

        then()
        assertNull(changed)
    }

    @Test
    fun `should delete user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub()) }

        whenn()
        suspendTransaction { fixture.sut.deleteUser(created.id) }
        val found = suspendTransaction { fixture.sut.getUserById(created.id) }

        then()
        assertNull(found)
    }
}
