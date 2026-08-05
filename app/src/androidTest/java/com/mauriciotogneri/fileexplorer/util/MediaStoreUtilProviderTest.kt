package com.mauriciotogneri.fileexplorer.util

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Exercises [MediaStoreUtil.notifyTreeDeleted] against the device's real media provider.
 *
 * The unit tests can only assert the selection string that is handed to the provider; nothing off
 * the device can answer whether the provider accepts it or how SQLite matches it. Both matter:
 * the provider unlinks the file backing every row it removes, so a selection that matches more
 * than the deleted tree deletes files the user never selected.
 *
 * Rows are created by writing files and letting the media scanner index them, which is how the
 * user's own files come to have one. Reaching shared storage at all needs All Files Access, an app
 * op that only exists from R, so earlier releases are skipped rather than covered by a second,
 * differently-shaped path through the same assertions.
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreUtilProviderTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val collection: Uri = MediaStore.Files.getContentUri("external")

    private val createdRoots = mutableListOf<File>()

    @Before
    fun setUp() {
        assumeTrue(
            "Reaching shared storage needs All Files Access, which only exists from R",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        )
        grantAllFilesAccess()
        assumeTrue(
            "All Files Access was not granted, so no external volume is visible",
            Environment.isExternalStorageManager()
        )
        // An emulator image can be built without emulated storage at all, and then there is no
        // volume for the provider to hold rows in — it rejects even the collection URI. Checked
        // separately from the app op: All Files Access reports as held either way.
        assumeTrue(
            "Device has no mounted shared storage",
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        )
    }

    /**
     * The app's only storage permission above Q is All Files Access, which is an app op rather than
     * a runtime permission, so `GrantPermissionRule` cannot grant it. Without it the provider
     * exposes no external volume to this package and rejects even the collection URI. Granting it
     * puts the test in the state the app is always in when it reaches this code.
     */
    private fun grantAllFilesAccess() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow"
        ).close()
        // The shell command runs on another process; give the op a moment to land.
        repeat(20) {
            if (Environment.isExternalStorageManager()) return
            Thread.sleep(100)
        }
    }

    @After
    fun tearDown() {
        createdRoots.forEach { root ->
            runCatching { root.deleteRecursively() }
            runCatching { runBlocking { MediaStoreUtil.notifyTreeDeleted(context, listOf(root.absolutePath)) } }
        }
    }

    @Test
    fun notifyTreeDeleted_dropsDescendantRows() = runBlocking {
        val stamp = System.currentTimeMillis()
        val treeName = "fe_tree_$stamp"
        val nested = createAndScan(treeName, "sub", "nested.txt")
        assumeTrue("Provider did not report a path for the row", nested != null)

        val treeRoot = treeRootOf(nested!!, treeName)
        assertTrue("Row should exist before the delete", rowExists(nested))

        // Mirror the real call order: the tree is already off disk when MediaStore is told.
        treeRoot.deleteRecursively()
        MediaStoreUtil.notifyTreeDeleted(context, listOf(treeRoot.absolutePath))

        assertFalse("Row below the deleted tree should be gone", rowExists(nested))
    }

    @Test
    fun notifyTreeDeleted_leavesRowsOutsideTheTree() = runBlocking {
        val stamp = System.currentTimeMillis()
        val treeName = "fe_tree_$stamp"
        val outsideName = "fe_other_$stamp"
        val inside = createAndScan(treeName, "sub", "nested.txt")
        val outside = createAndScan(outsideName, "sub", "nested.txt")
        assumeTrue("Provider did not report a path for the rows", inside != null && outside != null)

        val treeRoot = treeRootOf(inside!!, treeName)
        treeRoot.deleteRecursively()
        MediaStoreUtil.notifyTreeDeleted(context, listOf(treeRoot.absolutePath))

        assertFalse("Row inside the tree should be gone", rowExists(inside))
        assertTrue("Row in an unrelated directory must survive", rowExists(outside!!))
    }

    /**
     * The prefix is a wildcard pattern, so a directory whose own name contains pattern syntax must
     * be quoted before it is used as one. `[` opens a character class and, unlike `*` and `?`, is a
     * valid FAT file name character the provider does not rewrite — so `x[ab]y` is a directory a
     * user can really create, and an unquoted prefix built from it would also match `xay`.
     */
    @Test
    fun notifyTreeDeleted_doesNotMatchThroughWildcardsInTheDeletedName() = runBlocking {
        val stamp = System.currentTimeMillis()
        val patternName = "fe_x[ab]y_$stamp"
        val collateralName = "fe_xay_$stamp"
        val inPattern = createAndScan(patternName, "sub", "nested.txt")
        val collateral = createAndScan(collateralName, "sub", "nested.txt")
        assumeTrue(
            "Provider did not report a path for the rows",
            inPattern != null && collateral != null
        )
        assumeTrue(
            "Provider rewrote the wildcard out of the directory name",
            inPattern!!.contains("[ab]")
        )

        val treeRoot = treeRootOf(inPattern, patternName)
        treeRoot.deleteRecursively()
        MediaStoreUtil.notifyTreeDeleted(context, listOf(treeRoot.absolutePath))

        assertFalse("Row inside the deleted tree should be gone", rowExists(inPattern))
        // The failure this guards is not a stale row: the provider unlinks the file behind every
        // row it drops, so an unquoted prefix would delete this user's file too.
        assertTrue("Row only a wildcard could match must survive", rowExists(collateral!!))
    }

    /**
     * Writes a file at `Documents/<root>/<child>/<name>` and has the media scanner index it,
     * returning its path once a row exists — or null if the scan produced none.
     *
     * The row is built the way the user's own files get one, by scanning what is on disk, rather
     * than by inserting into the collection: `MediaStore.Files` takes queries and deletes but
     * refuses `insert`, and a scanned row is the shape the app actually meets.
     */
    private fun createAndScan(root: String, child: String, name: String): String? {
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "$root/$child/$name"
        )
        file.parentFile?.mkdirs()
        file.writeText("content")
        createdRoots.add(file.parentFile!!.parentFile!!)

        val scanned = CountDownLatch(1)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf("text/plain")
        ) { _, _ -> scanned.countDown() }
        scanned.await(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        return file.absolutePath.takeIf { rowExists(it) }
    }

    private fun rowExists(path: String): Boolean = context.contentResolver.query(
        collection,
        arrayOf(MediaStore.Files.FileColumns._ID),
        "${MediaStore.Files.FileColumns.DATA}=?",
        arrayOf(path),
        null
    )?.use { it.count > 0 } ?: false

    /**
     * Walks up from the inserted file to the directory named [root], which is the level the app
     * would report as deleted.
     */
    private fun treeRootOf(insertedPath: String, root: String): File {
        var candidate: File? = File(insertedPath).parentFile
        while (candidate != null && candidate.name != root) {
            candidate = candidate.parentFile
        }
        assertNotNull("Inserted path is not below $root", candidate)
        return candidate!!.also { createdRoots.add(it) }
    }

    private companion object {
        const val SCAN_TIMEOUT_SECONDS = 10L
    }
}
