package com.mauriciotogneri.fileexplorer.data.util

import android.database.sqlite.SQLiteException

/**
 * Returns true when [e] indicates a file the SQLite engine will not open as a database. These are
 * expected, unactionable conditions (not bugs) and must not be reported to crash analytics:
 *  - [android.database.sqlite.SQLiteDatabaseCorruptException] — `SQLITE_NOTADB` / `SQLITE_CORRUPT`,
 *    reported by SQLite as "file is encrypted or is not a database". The common case by far:
 *    [SqliteMetadataExtractor] probes by extension, and `.db` is claimed by plenty of formats that
 *    are not SQLite (Thumbs.db, LevelDB stores, app caches).
 *  - [android.database.sqlite.SQLiteCantOpenDatabaseException] — the path was deleted, its volume
 *    unmounted, or it stopped being readable between the existence check and the open.
 *  - Every other [SQLiteException] the engine raises over a file it cannot read: locked by another
 *    process, an unsupported file format, or a disk I/O error.
 *
 * Matched by type, as [isUnreadableZip] is: [SQLiteException] comes only from the SQLite engine,
 * and each of its subclasses describes the file or the device rather than anything this app did.
 * The guarded block in [SqliteMetadataExtractor.extract] holds a single call that can raise one —
 * `SQLiteDatabase.openDatabase` — because the query helpers swallow their own failures, so the type
 * match cannot silently absorb a bug from unrelated code. New callers must keep any other SQLite
 * work out of the guarded block, otherwise a genuine bug would be swallowed instead of reported.
 */
internal fun isUnreadableSqlite(e: Throwable): Boolean = e is SQLiteException
