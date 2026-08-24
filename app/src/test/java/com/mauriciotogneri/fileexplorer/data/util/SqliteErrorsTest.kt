package com.mauriciotogneri.fileexplorer.data.util

import android.database.SQLException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SqliteErrorsTest {

    @Test
    fun `isUnreadableSqlite returns true for a file that is not a database`() {
        // The reported non-fatal: SQLITE_NOTADB while opening a `.db` file holding other content.
        val e = SQLiteDatabaseCorruptException(
            "file is not a database (code 26 SQLITE_NOTADB[26]): , while compiling: PRAGMA cache_size"
        )
        assertTrue(isUnreadableSqlite(e))
    }

    @Test
    fun `isUnreadableSqlite returns true for a database that cannot be opened`() {
        assertTrue(isUnreadableSqlite(SQLiteCantOpenDatabaseException()))
    }

    @Test
    fun `isUnreadableSqlite returns true for engine failures over an unreadable file`() {
        assertTrue(isUnreadableSqlite(SQLiteDatabaseLockedException()))
        assertTrue(isUnreadableSqlite(SQLiteDiskIOException()))
    }

    @Test
    fun `isUnreadableSqlite returns true for any SQLiteException regardless of message`() {
        assertTrue(isUnreadableSqlite(SQLiteException()))
        assertTrue(isUnreadableSqlite(SQLiteException("unsupported file format")))
    }

    @Test
    fun `isUnreadableSqlite returns false for a plain SQLException`() {
        // The framework's broader SQLException does not come from opening a database file.
        assertFalse(isUnreadableSqlite(SQLException("no such column")))
    }

    @Test
    fun `isUnreadableSqlite returns false for unrelated exceptions`() {
        assertFalse(isUnreadableSqlite(IOException("failed to read the file")))
        assertFalse(isUnreadableSqlite(IllegalStateException("boom")))
        assertFalse(isUnreadableSqlite(RuntimeException()))
        assertFalse(isUnreadableSqlite(OutOfMemoryError()))
    }
}
