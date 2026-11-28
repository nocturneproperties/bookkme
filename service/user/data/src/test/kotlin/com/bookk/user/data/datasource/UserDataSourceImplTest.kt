package com.bookk.user.data.datasource

import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.data.map.toDomain
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.data.cache.UserCacheStrategy.getUser
import com.bookk.user.data.orm.table.UserTable
import com.bookk.user.domain.api.entity.ProfileName
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.entity.UserEditModel
import io.mockk.coEvery
import io.mockk.mockk
import library.encryption.Decrypted
import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys
import library.encryption.test.UnkeyedBlindIndex
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
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
    fun `should store the profile encrypted under the user's key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub(name = "Alice", lastName = "Smith", email = "alice@example.com", phone = "+10000000000")

        whenn()
        suspendTransaction { fixture.sut.insertNewUser(user) }

        then()
        val row = fixture.storedRow(user.id)
        assertEquals("Alice", fixture.decrypted(user.id, row[UserTable.nameEncrypted]))
        assertEquals("Smith", fixture.decrypted(user.id, row[UserTable.lastNameEncrypted]))
        assertEquals("alice@example.com", fixture.decrypted(user.id, row[UserTable.emailEncrypted]))
        assertEquals("+10000000000", fixture.decrypted(user.id, row[UserTable.phoneEncrypted]))
    }

    @Test
    fun `should store the Telegram user id encrypted under the user's key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub(telegramUserId = 123456789)

        whenn()
        suspendTransaction { fixture.sut.insertNewUser(user) }
        val found = suspendTransaction { fixture.uncached.getUserById(user.id) }

        then()
        val row = fixture.storedRow(user.id)
        assertEquals("123456789", fixture.decrypted(user.id, row[UserTable.telegramUserIdEncrypted]))
        assertEquals(123456789L, found!!.telegramUserId)
    }

    @Test
    fun `should store no Telegram user id for a user who signed up by email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub(telegramUserId = null)

        whenn()
        suspendTransaction { fixture.sut.insertNewUser(user) }

        then()
        assertNull(fixture.storedRow(user.id)[UserTable.telegramUserIdEncrypted])
    }

    @Test
    fun `should keep the stored Telegram user id ciphertext through a profile edit and an email change`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            val user = suspendTransaction { fixture.sut.insertNewUser(User.stub(telegramUserId = 123456789)) }
            val stored = fixture.storedRow(user.id)[UserTable.telegramUserIdEncrypted]

            whenn()
            val edited = suspendTransaction { fixture.sut.updateUser(user.id, nameEdit("Alicia"), Clock.System.now()) }
            val changed = suspendTransaction {
                fixture.sut.changeEmail(user.id, "new@example.com", Clock.System.now())
            }

            then()
            assertEquals(123456789L, edited!!.telegramUserId)
            assertEquals(123456789L, changed!!.telegramUserId)
            assertArrayEquals(stored, fixture.storedRow(user.id)[UserTable.telegramUserIdEncrypted])
        }

    @Test
    fun `should read no Telegram user id and report the missing key when the profile's key was lost`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            val user = suspendTransaction { fixture.sut.insertNewUser(User.stub(telegramUserId = 123456789)) }
            fixture.subjectKeys.loseKey(user.id)

            whenn()
            val found = suspendTransaction { fixture.uncached.getUserById(user.id) }

            then()
            assertNull(found!!.telegramUserId)
            assertEquals(listOf("profile"), fixture.missingKeys.reported)
        }

    @Test
    fun `should store the Telegram username encrypted under the user's key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub(telegramUserId = 123456789, telegramUsername = "ann_lee")

        whenn()
        suspendTransaction { fixture.sut.insertNewUser(user) }
        val found = suspendTransaction { fixture.uncached.getUserById(user.id) }

        then()
        val row = fixture.storedRow(user.id)
        assertEquals("ann_lee", fixture.decrypted(user.id, row[UserTable.telegramUsernameEncrypted]))
        assertEquals("ann_lee", found!!.telegramUsername)
    }

    @Test
    fun `should store a changed Telegram username and report the change`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = suspendTransaction { fixture.sut.insertNewUser(User.stub(telegramUsername = "ann_lee")) }
        val updatedAt = Instant.fromEpochSeconds(1_800_000_000)

        whenn()
        val change = suspendTransaction { fixture.sut.changeTelegramUsername(user.id, "ann_renamed", updatedAt) }

        then()
        assertTrue(change!!.changed)
        assertEquals(user.copy(telegramUsername = "ann_renamed"), change.user)
        val row = fixture.storedRow(user.id)
        assertEquals("ann_renamed", fixture.decrypted(user.id, row[UserTable.telegramUsernameEncrypted]))
        assertEquals(updatedAt, row[UserTable.updatedAt])
        assertEquals("ann_renamed", suspendTransaction { fixture.sut.getUserById(user.id) }!!.telegramUsername)
    }

    @Test
    fun `should leave the profile untouched when the Telegram username is unchanged`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = suspendTransaction { fixture.sut.insertNewUser(User.stub(telegramUsername = "ann_lee")) }
        val before = fixture.storedRow(user.id)

        whenn()
        val change = suspendTransaction {
            fixture.sut.changeTelegramUsername(user.id, "ann_lee", Instant.fromEpochSeconds(1_800_000_000))
        }

        then()
        assertEquals(false, change!!.changed)
        assertEquals(user, change.user)
        val after = fixture.storedRow(user.id)
        assertArrayEquals(before[UserTable.telegramUsernameEncrypted], after[UserTable.telegramUsernameEncrypted])
        assertEquals(before[UserTable.updatedAt], after[UserTable.updatedAt])
    }

    @Test
    fun `should clear the Telegram username the user removed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = suspendTransaction { fixture.sut.insertNewUser(User.stub(telegramUsername = "ann_lee")) }

        whenn()
        val change = suspendTransaction { fixture.sut.changeTelegramUsername(user.id, null, Clock.System.now()) }

        then()
        assertTrue(change!!.changed)
        assertNull(change.user.telegramUsername)
        assertNull(fixture.storedRow(user.id)[UserTable.telegramUsernameEncrypted])
    }

    @Test
    fun `should keep the Telegram username through a profile edit and an email change`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = suspendTransaction { fixture.sut.insertNewUser(User.stub(telegramUsername = "ann_lee")) }

        whenn()
        suspendTransaction { fixture.sut.updateUser(user.id, nameEdit("Alicia"), Clock.System.now()) }
        suspendTransaction { fixture.sut.changeEmail(user.id, "new@example.com", Clock.System.now()) }

        then()
        val row = fixture.storedRow(user.id)
        assertEquals("ann_lee", fixture.decrypted(user.id, row[UserTable.telegramUsernameEncrypted]))
    }

    @Test
    fun `should return null when changing the Telegram username of a missing user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val change = suspendTransaction {
            fixture.sut.changeTelegramUsername(Uuid.random(), "ann_lee", Clock.System.now())
        }

        then()
        assertNull(change)
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
    fun `should read empty details and report the missing key when a profile's key was lost`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub(name = "Alice", email = "alice@example.com", phone = "+10000000000")
        suspendTransaction { fixture.sut.insertNewUser(user) }
        fixture.subjectKeys.loseKey(user.id)

        whenn()
        val found = suspendTransaction { fixture.uncached.getUserById(user.id) }

        then()
        assertEquals(
            User.stub(id = user.id, name = "", lastName = "", email = "", phone = null),
            found,
        )
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
    fun `should keep the stored fields a re-entry over a lost key does not change`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub(name = "Alice", lastName = "Smith", email = "alice@example.com", phone = "+10000000000")
        suspendTransaction { fixture.sut.insertNewUser(user) }
        val before = fixture.storedRow(user.id)
        fixture.subjectKeys.loseKey(user.id)

        whenn()
        suspendTransaction { fixture.uncached.updateUser(user.id, nameEdit("Alicia"), Clock.System.now()) }

        then()
        val after = fixture.storedRow(user.id)
        assertArrayEquals(before[UserTable.lastNameEncrypted], after[UserTable.lastNameEncrypted])
        assertArrayEquals(before[UserTable.emailEncrypted], after[UserTable.emailEncrypted])
        assertArrayEquals(before[UserTable.phoneEncrypted], after[UserTable.phoneEncrypted])
        assertEquals("Alicia", fixture.decrypted(user.id, after[UserTable.nameEncrypted]))
    }

    @Test
    fun `should keep reporting the missing key of a profile whose re-entry left fields unreadable`() =
        runUnitTest {
            given()
            val fixture = SutFixture()
            val user = User.stub(name = "Alice", lastName = "Smith", email = "alice@example.com")
            suspendTransaction { fixture.sut.insertNewUser(user) }
            fixture.subjectKeys.loseKey(user.id)
            suspendTransaction { fixture.uncached.updateUser(user.id, nameEdit("Alicia"), Clock.System.now()) }
            val reportedBefore = fixture.missingKeys.reported.size

            whenn()
            val found = suspendTransaction { fixture.uncached.getUserById(user.id) }

            then()
            assertEquals(
                User.stub(id = user.id, name = "Alicia", lastName = "", email = "", phone = null),
                found,
            )
            assertEquals(listOf("profile"), fixture.missingKeys.reported.drop(reportedBefore))
        }

    @Test
    fun `should not cache a profile read while its key is missing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub(name = "Alice", email = "alice@example.com")
        suspendTransaction { fixture.uncached.insertNewUser(user) }
        fixture.subjectKeys.loseKey(user.id)

        whenn()
        suspendTransaction { fixture.sut.getUserById(user.id) }

        then()
        assertNull(fixture.cacheClient.getUser(user.id))
    }

    @Test
    fun `should fail the deletion when the cached profile cannot be evicted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val cacheClient = mockk<CacheClient<String>>()
        coEvery { cacheClient.delete(any()) } throws IllegalStateException("cache unavailable")
        val sut = UserDataSourceImpl(cacheClient, fixture.profileCipher)
        val user = suspendTransaction { fixture.uncached.insertNewUser(User.stub()) }

        whenn()
        val result = runCatching { suspendTransaction { sut.deleteUser(user.id) } }

        then()
        assertTrue(result.isFailure)
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
    fun `should replace the name together with the email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub(name = "Demo", lastName = "")) }
        val name = ProfileName.stub(name = "Ann", lastName = "Lee")

        whenn()
        suspendTransaction { fixture.sut.changeEmailAndName(created.id, "ann@example.com", name, Clock.System.now()) }
        val found = suspendTransaction { fixture.uncached.getUserById(created.id) }

        then()
        assertEquals(listOf("Ann", "Lee", "ann@example.com"), listOf(found?.name, found?.lastName, found?.email))
    }

    @Test
    fun `should keep the name when another user already holds the new email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "taken@example.com")) }
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub(name = "Demo", lastName = "")) }
        val name = ProfileName.stub(name = "Ann", lastName = "Lee")

        whenn()
        val result = runCatching {
            suspendTransaction {
                fixture.sut.changeEmailAndName(created.id, "taken@example.com", name, Clock.System.now())
            }
        }
        val found = suspendTransaction { fixture.uncached.getUserById(created.id) }

        then()
        assertTrue(result.exceptionOrNull()?.toDomain() is Error.UniqueConstraintFailed) { result.toString() }
        assertEquals("Demo", found?.name)
    }

    @Test
    fun `should return null when changing the email and name of a missing user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val changed = suspendTransaction {
            fixture.sut.changeEmailAndName(Uuid.random(), "ann@example.com", ProfileName.stub(), Clock.System.now())
        }

        then()
        assertNull(changed)
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
