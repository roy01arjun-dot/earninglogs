package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.WageType
import com.example.data.WorkLogDao
import com.example.data.WorkLogDatabase
import com.example.data.WorkLogEntity
import com.example.util.PinHasher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityAndSyncTest {

    private lateinit var db: WorkLogDatabase
    private lateinit var dao: WorkLogDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WorkLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.workLogDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---------------------------------------------------------------- PIN

    @Test
    fun `pin verifies against its own salted hash`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("8321", salt)
        assertTrue(PinHasher.verify("8321", salt, hash))
    }

    @Test
    fun `wrong pin is rejected`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("8321", salt)
        assertFalse(PinHasher.verify("8322", salt, hash))
        assertFalse(PinHasher.verify("", salt, hash))
    }

    /**
     * Regression test for the removed backdoor. The previous implementation accepted "1234" or
     * "0000" on any device whenever no custom PIN was set, and compared cleartext otherwise.
     */
    @Test
    fun `hardcoded master pins no longer unlock anything`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("8321", salt)
        assertFalse(PinHasher.verify("1234", salt, hash))
        assertFalse(PinHasher.verify("0000", salt, hash))
    }

    @Test
    fun `verify fails closed when no pin is configured`() {
        assertFalse(PinHasher.verify("1234", "", ""))
        assertFalse(PinHasher.verify("0000", PinHasher.newSalt(), ""))
    }

    @Test
    fun `same pin under different salts produces different hashes`() {
        val a = PinHasher.newSalt()
        val b = PinHasher.newSalt()
        assertNotEquals(a, b)
        assertNotEquals(PinHasher.hash("8321", a), PinHasher.hash("8321", b))
    }

    // ---------------------------------------------------------------- sync

    private fun entry(
        uuid: String,
        date: String = "2026-08-10",
        hours: Double = 8.0,
        updatedAt: Long = 1_000L,
        deletedAt: Long? = null,
        pending: Boolean = false,
        note: String = ""
    ) = WorkLogEntity(
        id = 0,
        uuid = uuid,
        date = date,
        hoursWorked = hours,
        wageType = WageType.HOURLY,
        wageRate = 25_000.0,
        note = note,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        pendingSync = pending
    )

    @Test
    fun `newer remote row overwrites older local row`() = runTest {
        dao.insertLog(entry("u1", hours = 8.0, updatedAt = 1_000L, pending = false))
        dao.applyRemote(entry("u1", hours = 9.5, updatedAt = 2_000L))

        val stored = dao.getLogByUuid("u1")
        assertNotNull(stored)
        assertEquals(9.5, stored!!.hoursWorked, 0.001)
    }

    @Test
    fun `older remote row does not clobber newer local row`() = runTest {
        dao.insertLog(entry("u1", hours = 9.5, updatedAt = 5_000L, pending = false))
        dao.applyRemote(entry("u1", hours = 8.0, updatedAt = 2_000L))

        assertEquals(9.5, dao.getLogByUuid("u1")!!.hoursWorked, 0.001)
    }

    /**
     * The case that matters offline: an edit made on the plane must survive the first pull
     * after landing, even though the server copy is "newer" by wall clock in some skew cases.
     */
    @Test
    fun `unpushed local edit wins over remote at same timestamp`() = runTest {
        dao.insertLog(entry("u1", hours = 12.0, updatedAt = 3_000L, pending = true))
        dao.applyRemote(entry("u1", hours = 8.0, updatedAt = 3_000L))

        val stored = dao.getLogByUuid("u1")!!
        assertEquals(12.0, stored.hoursWorked, 0.001)
        assertTrue("local edit must stay queued for upload", stored.pendingSync)
    }

    @Test
    fun `unknown remote row is inserted`() = runTest {
        dao.applyRemote(entry("fresh", hours = 7.0, updatedAt = 9_000L))

        val stored = dao.getLogByUuid("fresh")
        assertNotNull(stored)
        assertEquals(7.0, stored!!.hoursWorked, 0.001)
        assertFalse("a row that came from the server is not pending", stored.pendingSync)
    }

    @Test
    fun `soft deleted rows disappear from reads but remain as tombstones`() = runTest {
        val id = dao.insertLog(entry("u1")).toInt()
        assertEquals(1, dao.getAllLogsOnce().size)

        dao.softDeleteById(id, now = 4_000L)

        assertTrue("deleted rows must not appear in the UI", dao.getAllLogsOnce().isEmpty())
        val tombstone = dao.getLogByUuid("u1")
        assertNotNull("tombstone must survive so the delete can reach other devices", tombstone)
        assertEquals(4_000L, tombstone!!.deletedAt)
        assertTrue(tombstone.pendingSync)
    }

    @Test
    fun `remote tombstone removes a row from this device`() = runTest {
        dao.insertLog(entry("u1", updatedAt = 1_000L, pending = false))
        assertEquals(1, dao.getAllLogsOnce().size)

        dao.applyRemote(entry("u1", updatedAt = 2_000L, deletedAt = 2_000L))

        assertTrue(dao.getAllLogsOnce().isEmpty())
    }

    @Test
    fun `pushed tombstones are purged only after the retention window`() = runTest {
        val id = dao.insertLog(entry("u1")).toInt()
        dao.softDeleteById(id, now = 1_000L)
        dao.clearPendingFlags(listOf("u1"))

        dao.purgeSyncedTombstones(before = 500L)
        assertNotNull("too new to purge", dao.getLogByUuid("u1"))

        dao.purgeSyncedTombstones(before = 5_000L)
        assertNull("old, already-pushed tombstone should be gone", dao.getLogByUuid("u1"))
    }

    @Test
    fun `unpushed tombstones are never purged`() = runTest {
        val id = dao.insertLog(entry("u1")).toInt()
        dao.softDeleteById(id, now = 1_000L) // leaves pendingSync = true

        dao.purgeSyncedTombstones(before = 9_999_999L)

        assertNotNull(
            "purging an unpushed tombstone would lose the delete forever",
            dao.getLogByUuid("u1")
        )
    }

    @Test
    fun `pending query returns tombstones as well as edits`() = runTest {
        dao.insertLog(entry("keep", pending = true))
        val id = dao.insertLog(entry("gone", pending = false)).toInt()
        dao.softDeleteById(id)

        val pending = dao.getPendingSync().map { it.uuid }.toSet()
        assertEquals(setOf("keep", "gone"), pending)
    }

    @Test
    fun `renaming a workplace marks affected rows for re-upload`() = runTest {
        dao.insertLog(entry("u1").copy(workplace = "Old Site", pendingSync = false))
        dao.insertLog(entry("u2").copy(workplace = "Other", pendingSync = false))

        dao.updateWorkplaceName("Old Site", "New Site", now = 7_000L)

        val renamed = dao.getLogByUuid("u1")!!
        assertEquals("New Site", renamed.workplace)
        assertTrue(renamed.pendingSync)
        assertFalse("untouched rows stay clean", dao.getLogByUuid("u2")!!.pendingSync)
    }
}
