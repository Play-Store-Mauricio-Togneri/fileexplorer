package com.mauriciotogneri.fileexplorer.data.util

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.mauriciotogneri.fileexplorer.data.model.SqliteMetadata
import java.io.File

object SqliteMetadataExtractor {

    private const val MAX_TABLES_TO_SHOW = 10

    /**
     * No-op corruption handler. The 3-arg [SQLiteDatabase.openDatabase] installs the framework
     * `DefaultDatabaseErrorHandler`, whose `onCorruption` DELETES the database file (and its
     * `-journal`/`-wal`/`-shm` sidecars) when SQLite reports corruption (`SQLITE_CORRUPT` /
     * `SQLITE_NOTADB`). This is a read-only metadata probe over arbitrary user files, so it must
     * never mutate or delete them: swallow the callback and let the normal error-handling flow run
     * (the corruption exception is still thrown and caught in [extract]/[getTableNames]).
     */
    private val nonDestructiveErrorHandler = DatabaseErrorHandler { }

    fun extract(file: File): SqliteMetadata? {
        if (!file.exists() || !file.canRead()) return null

        var database: SQLiteDatabase? = null
        return try {
            database = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                nonDestructiveErrorHandler
            )

            val tableNames = getTableNames(database)
            val totalRowCount = getTotalRowCount(database, tableNames)

            SqliteMetadata(
                tableCount = tableNames.size.takeIf { it > 0 },
                tableNames = tableNames.take(MAX_TABLES_TO_SHOW).takeIf { it.isNotEmpty() },
                totalRowCount = totalRowCount.takeIf { it > 0 }
            )
        } catch (e: Exception) {
            ErrorReporter.warning(e, "extract_sqlite_metadata", "sqlite")
            null
        } finally {
            try {
                database?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun getTableNames(database: SQLiteDatabase): List<String> {
        val tables = mutableListOf<String>()
        try {
            database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' ORDER BY name",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    tables.add(cursor.getString(0))
                }
            }
        } catch (e: Exception) {
            // Ignore query errors
        }
        return tables
    }

    private fun getTotalRowCount(database: SQLiteDatabase, tableNames: List<String>): Long {
        var totalRows = 0L
        for (tableName in tableNames) {
            try {
                val safeName = tableName.replace("\"", "\"\"")
                database.rawQuery("SELECT COUNT(*) FROM \"$safeName\"", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        totalRows += cursor.getLong(0)
                    }
                }
            } catch (e: Exception) {
                // Ignore individual table errors
            }
        }
        return totalRows
    }
}
