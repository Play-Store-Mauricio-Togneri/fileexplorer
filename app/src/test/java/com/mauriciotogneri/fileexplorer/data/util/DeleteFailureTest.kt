package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The precedence that decides what a delete that left something behind tells the user.
 *
 * Only `null` and [ERRNO_UNKNOWN] are asserted to classify: every [android.system.OsConstants] field
 * reads 0 on the JVM, so any other errno lands on [DeleteFailure.OTHER] here whatever it means on a
 * device. The errno-specific mapping is covered on a device by `FileAccessTest`.
 */
class DeleteFailureTest {

    private val someErrno = 30

    @Test
    fun `a visible failure outranks a protected folder and an unlisted one`() {
        val failure = deleteFailureFor(
            unexplainedErrno = ERRNO_UNKNOWN,
            systemFolders = 1,
            unlistedFolders = 1,
            firstErrno = someErrno
        )

        assertEquals(DeleteFailure.UNKNOWN, failure)
    }

    @Test
    fun `a protected folder outranks an unlisted one`() {
        val failure = deleteFailureFor(
            unexplainedErrno = null,
            systemFolders = 1,
            unlistedFolders = 2,
            firstErrno = someErrno
        )

        assertEquals(DeleteFailure.SYSTEM_FOLDER, failure)
    }

    @Test
    fun `an unlisted folder alone reports hidden contents`() {
        val failure = deleteFailureFor(
            unexplainedErrno = null,
            systemFolders = 0,
            unlistedFolders = 1,
            firstErrno = someErrno
        )

        assertEquals(DeleteFailure.HIDDEN_CONTENTS, failure)
    }

    @Test
    fun `a result without the walk's findings classifies its first errno as before`() {
        assertEquals(DeleteFailure.UNKNOWN, deleteFailureFor(null, 0, 0, ERRNO_UNKNOWN))
        assertEquals(DeleteFailure.UNKNOWN, deleteFailureFor(null, 0, 0, null))
        assertEquals(deleteFailureFor(someErrno), deleteFailureFor(null, 0, 0, someErrno))
    }

    @Test
    fun `only the two causes Android imposes are kept by Android`() {
        val kept = DeleteFailure.entries.filter { it.keptByAndroid }

        assertEquals(listOf(DeleteFailure.SYSTEM_FOLDER, DeleteFailure.HIDDEN_CONTENTS), kept)
    }

    @Test
    fun `the new causes carry the analytics labels the dashboard reads`() {
        assertEquals("system_folder", DeleteFailure.SYSTEM_FOLDER.analyticsLabel)
        assertEquals("hidden_contents", DeleteFailure.HIDDEN_CONTENTS.analyticsLabel)
    }
}
