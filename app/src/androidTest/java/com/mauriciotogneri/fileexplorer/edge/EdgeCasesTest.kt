package com.mauriciotogneri.fileexplorer.edge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.util.zip.ZipFile

// device-required: these drive FileRepository's rename paths, and Build.VERSION.SDK_INT is 0 on
// the JVM, so the Files.move(ATOMIC_MOVE) branch of renameRegular is unreachable in the unit
// suite — only a device exercises the primary path. The case-only renames need storage that folds
// case, the unicode and malformed names the platform's own filename encoding, and the symlink cases
// the java.nio branch of isSymlink. Everything that needs none of these lives in
// FileRepositoryOperationsTest on the JVM.
@RunWith(AndroidJUnit4::class)
class EdgeCasesTest {

    private lateinit var testDir: File
    private lateinit var sourceDir: File
    private lateinit var targetDir: File
    private lateinit var caseFoldingDir: File
    private lateinit var fileRepository: FileRepository
    private lateinit var allowedRoots: List<String>

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(context.cacheDir, "test_edge_${System.currentTimeMillis()}")
        testDir.mkdirs()

        sourceDir = File(testDir, "source")
        sourceDir.mkdirs()

        targetDir = File(testDir, "target")
        targetDir.mkdirs()

        // App-specific external storage: emulated, so it folds case the way shared storage does,
        // and writable without any storage permission.
        caseFoldingDir = File(context.getExternalFilesDir(null), "test_case_rename_${System.currentTimeMillis()}")
        caseFoldingDir.mkdirs()

        fileRepository = FileRepository()
        allowedRoots = listOf(testDir.absolutePath)
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
        caseFoldingDir.deleteRecursively()
    }

    // region Case-Only Rename Tests

    /*
     * Run on storage that folds case, because that is the only place the case-only branch matters.
     * There `File("testfile.txt").exists()` is already true before renaming `TestFile.txt`, so the
     * regular rename path's "target exists" guard refuses the rename; only the two-hop
     * `renameCaseOnly` gets past it. Removing that branch would make every case-only rename on
     * shared storage fail. These tests used to run in cacheDir, which is case-sensitive, so they
     * passed either way.
     *
     * The listing is what is asserted, not `exists()`: on this storage `exists()` answers true for
     * any casing, the old one included, and a rename stuck halfway leaves a `.tmp_rename_*` file.
     */

    @Test
    fun caseOnlyRename_toLowercase_works() = runBlocking {
        assertCaseOnlyRenameLands(createTestFile(caseFoldingDir, "TestFile.txt", "content"), "testfile.txt")
    }

    @Test
    fun caseOnlyRename_toUppercase_works() = runBlocking {
        assertCaseOnlyRenameLands(createTestFile(caseFoldingDir, "document.txt", "content"), "DOCUMENT.TXT")
    }

    @Test
    fun caseOnlyRename_mixedCase_works() = runBlocking {
        assertCaseOnlyRenameLands(createTestFile(caseFoldingDir, "readme.txt", "content"), "ReadMe.TXT")
    }

    @Test
    fun caseOnlyRename_folder_works() = runBlocking {
        val originalFolder = File(caseFoldingDir, "documents")
        originalFolder.mkdirs()

        assertCaseOnlyRenameLands(originalFolder, "Documents")

        assertTrue("Should still be a directory", File(caseFoldingDir, "Documents").isDirectory)
    }

    private suspend fun assertCaseOnlyRenameLands(original: File, newName: String) {
        assertStorageFoldsCase()

        val result = fileRepository.rename(FileItem.from(original), newName)

        assertNotNull("Rename should succeed", result)
        assertEquals(
            "The folder should hold exactly the new casing, with nothing left halfway",
            listOf(newName),
            caseFoldingDir.list()?.toList()
        )
    }

    /**
     * Fails rather than skips: on storage that does not fold case these tests would pass without
     * ever needing the case-only branch, which is the false green they were moved here to end.
     */
    private fun assertStorageFoldsCase() {
        val probe = createTestFile(caseFoldingDir, "case_probe.txt", "probe")
        val folds = File(caseFoldingDir, "CASE_PROBE.TXT").exists()
        probe.delete()
        assertTrue(
            "${caseFoldingDir.parent} does not fold case, so a case-only rename here never needs " +
                "the case-only branch. Run on an emulator or device with emulated external storage.",
            folds
        )
    }

    // endregion

    // region Very Long Filename Tests

    @Test
    fun veryLongFileName_rename_works() = runBlocking {
        val originalName = "original.txt"
        val longName = "C".repeat(100) + ".txt"
        val originalFile = createTestFile(sourceDir, originalName, "content")
        val fileItem = FileItem.from(originalFile)

        val result = fileRepository.rename(fileItem, longName)

        assertNotNull("Rename to long name should succeed", result)
        val renamedFile = File(sourceDir, longName)
        assertTrue("Renamed file with long name should exist", renamedFile.exists())
    }

    // endregion

    // region Unicode Filename Tests

    /**
     * Listed through the repository, not read back off `File`. Asserting `exists()` and `readText()`
     * on a file the test just wrote exercises `java.io.File` and would hold with the app deleted;
     * what matters is that a name outside ASCII survives the listing the folder screen renders. A
     * `listFiles` that dropped or normalised such names — a filter, or a byte round-trip — makes
     * these files vanish from every folder in the app, and only this notices.
     */
    @Test
    fun unicodeFileName_listedThroughTheRepository_keepsItsName() = runBlocking {
        val unicodeName = "文档_📁_αβγ.txt"
        createTestFile(sourceDir, unicodeName, "unicode content")

        val listed = fileRepository.listFiles(sourceDir.absolutePath, showHidden = false, sortMode = SortMode.NAME_ASC)

        assertTrue(
            "The unicode name should survive the listing, got ${listed.map { it.name }}",
            listed.any { it.name == unicodeName }
        )
    }

    @Test
    fun unicodeFileName_emojiOnly_listedThroughTheRepository_keepsItsName() = runBlocking {
        val emojiName = "📄🎵🎬.txt"
        createTestFile(sourceDir, emojiName, "emoji content")

        val listed = fileRepository.listFiles(sourceDir.absolutePath, showHidden = false, sortMode = SortMode.NAME_ASC)

        assertTrue(
            "The emoji name should survive the listing, got ${listed.map { it.name }}",
            listed.any { it.name == emojiName }
        )
    }

    @Test
    fun unicodeFileName_copy_works() = runBlocking {
        val unicodeName = "日本語_ファイル.txt"
        val unicodeFile = createTestFile(sourceDir, unicodeName, "japanese content")
        val sourceItem = FileItem.from(unicodeFile)

        fileRepository.copyFiles(
            sources = listOf(sourceItem),
            targetDir = targetDir.absolutePath,
            deleteAfter = false,
            allowedRoots = allowedRoots
        ).toList()

        val copiedFile = File(targetDir, unicodeName)
        assertTrue("Unicode filename file should be copied", copiedFile.exists())
        assertEquals("Content should match", "japanese content", copiedFile.readText())
    }

    @Test
    fun unicodeFileName_rename_works() = runBlocking {
        val originalName = "original.txt"
        val unicodeName = "переименованный_файл.txt"
        val originalFile = createTestFile(sourceDir, originalName, "content")
        val fileItem = FileItem.from(originalFile)

        val result = fileRepository.rename(fileItem, unicodeName)

        assertNotNull("Rename to unicode name should succeed", result)
        val renamedFile = File(sourceDir, unicodeName)
        assertTrue("Renamed file with unicode name should exist", renamedFile.exists())
    }

    @Test
    fun unicodeFileName_arabicRtl_listedThroughTheRepository_keepsItsName() = runBlocking {
        val arabicName = "ملف_عربي.txt"
        createTestFile(sourceDir, arabicName, "arabic content")

        val listed = fileRepository.listFiles(sourceDir.absolutePath, showHidden = false, sortMode = SortMode.NAME_ASC)

        assertTrue(
            "The Arabic name should survive the listing, got ${listed.map { it.name }}",
            listed.any { it.name == arabicName }
        )
    }

    /**
     * Names whose bytes are not valid UTF-8 (downloads truncated mid-character, for example) come
     * back from `listFiles()` as unpaired surrogates that `File.toPath()` rejects, which used to
     * crash every recursive walk on API 26+.
     */
    @Test
    fun malformedFileName_recursiveWalk_doesNotSkipTheEntry() = runBlocking {
        val malformedEntry = createMalformedFile()
        assumeTrue("Filesystem normalized the malformed name", malformedEntry != null)
        val folderItem = FileItem.from(sourceDir)

        val size = fileRepository.totalSize(listOf(folderItem))
        val count = fileRepository.totalNodeCount(listOf(folderItem))

        // The entry must be walked like any other file: reporting it as a symlink would make copy,
        // compress and search drop it silently.
        assertEquals("Malformed name should be measured", MALFORMED_CONTENT.length.toLong(), size)
        // The folder plus the malformed entry inside it.
        assertEquals("Malformed name should be counted", 2, count)
    }

    @Test
    fun malformedFileName_rename_reportsOutcomeWithoutCrashing() = runBlocking {
        val malformedEntry = createMalformedFile()
        assumeTrue("Filesystem normalized the malformed name", malformedEntry != null)

        val result = fileRepository.rename(FileItem.from(malformedEntry!!), "renamed.txt")

        // The rename can legitimately fail when the name cannot be re-encoded to the on-disk bytes;
        // either way the outcome must match what is on disk instead of throwing.
        if (result != null) {
            assertTrue("Renamed file should exist", File(sourceDir, "renamed.txt").exists())
            assertNull("Original entry should be gone", listMalformedEntry())
        } else {
            assertNotNull("Original entry should remain", listMalformedEntry())
        }
    }

    /**
     * Creates a file in [sourceDir] whose name is not valid UTF-8, or returns null when the
     * filesystem rejected or normalized the name.
     */
    private fun createMalformedFile(): File? {
        try {
            createTestFile(sourceDir, MALFORMED_NAME, MALFORMED_CONTENT)
        } catch (_: IOException) {
            return null
        }
        return listMalformedEntry()
    }

    /**
     * Returns the entry in [sourceDir] whose name cannot be converted to a `java.nio` path, or null
     * when there is none.
     */
    private fun listMalformedEntry(): File? = sourceDir.listFiles()?.firstOrNull { entry ->
        try {
            entry.toPath()
            false
        } catch (_: InvalidPathException) {
            true
        }
    }

    // endregion

    // region Empty File Tests

    @Test
    fun emptyFile_rename_works() = runBlocking {
        val emptyFile = File(sourceDir, "empty_original.txt")
        emptyFile.createNewFile()
        val fileItem = FileItem.from(emptyFile)

        val result = fileRepository.rename(fileItem, "empty_renamed.txt")

        assertNotNull("Rename should succeed", result)
        assertTrue("Renamed empty file should exist", File(sourceDir, "empty_renamed.txt").exists())
    }

    // endregion

    // region Node Count Tests

    /**
     * On the JVM `Build.VERSION.SDK_INT` is 0, so the unit tests only ever reach the pre-O
     * canonical-path branch of the symlink check. This runs the `java.nio` branch the app actually
     * uses, against the count that decides whether a delete gets a progress dialog it can be
     * cancelled from.
     */
    @Test
    fun totalNodeCount_doesNotFollowOrCountSymlink() = runBlocking {
        val outside = File(testDir, "outside")
        outside.mkdirs()
        createTestFile(outside, "hidden.txt", "content")
        val folder = File(sourceDir, "withLink")
        folder.mkdirs()
        createTestFile(folder, "real.txt", "content")
        val link = File(folder, "link")
        val created = try {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            true
        } catch (_: Exception) {
            false
        }
        assumeTrue(
            "Filesystem does not support symbolic links",
            created && Files.isSymbolicLink(link.toPath())
        )

        val count = fileRepository.totalNodeCount(listOf(FileItem.from(folder)))

        // The folder and `real.txt`; the symlink is neither counted nor descended into, so the
        // file behind it never reaches the total.
        assertEquals("Symlink must not be counted or followed", 2, count)
    }

    // endregion

    // region Symlink Handling On The Destructive Walks

    /**
     * The counter above is not the walk that can lose data. `FileRepository.deleteRecursive` decides
     * whether to descend with the same `isSymlink()` call, and a symlinked directory it took for a
     * plain one is descended into and emptied — so a delete the user aimed at one folder unlinks
     * every file in whatever that link points at, outside the selection and with no undo.
     *
     * On the JVM `Build.VERSION.SDK_INT` is 0, so the unit suite only ever reaches the pre-O
     * canonical-path branch of that check. That branch is not dead — `minSdk` is 24 and the
     * `java.nio` arm starts at 26, so API 24 and 25 devices run it in production, and the unit
     * tests are the only thing covering them. What no unit test can reach is the `java.nio` branch
     * every other device takes, which is what these three run.
     */
    @Test
    fun delete_symlinkedDirectory_unlinksTheLinkAndLeavesItsTargetIntact() = runBlocking {
        val linkTarget = createLinkTarget()
        val folder = createFolderWithSymlinkTo("withLinkDeleted", linkTarget)
        assumeTrue("Filesystem does not support symbolic links", folder != null)

        val result = fileRepository.delete(listOf(FileItem.from(folder!!)))

        assertTrue("The selected folder should be deleted", result.success)
        assertFalse("The selected folder should be gone", folder.exists())
        assertLinkTargetIntact(linkTarget)
    }

    /**
     * The same guard on the transfer walk, where the loss is worse than on the delete: a move that
     * mistook the link for a directory would copy the target's files into the destination and then
     * delete the originals, emptying a directory the user never selected and leaving the copies
     * under a name they never chose. `copyRecursive` treats a symlink as a leaf — the link is
     * dropped rather than followed or recreated — so nothing behind it moves.
     *
     * Driven with `deleteAfter = true` because that is the destructive half: a plain copy over a
     * misread link duplicates data, a move destroys it.
     */
    @Test
    fun move_symlinkedDirectory_leavesTheLinkTargetIntact() = runBlocking {
        val linkTarget = createLinkTarget()
        val folder = createFolderWithSymlinkTo("withLinkMoved", linkTarget)
        assumeTrue("Filesystem does not support symbolic links", folder != null)

        fileRepository.copyFiles(
            sources = listOf(FileItem.from(folder!!)),
            targetDir = targetDir.absolutePath,
            deleteAfter = true,
            allowedRoots = allowedRoots
        ).toList()

        val moved = File(targetDir, "withLinkMoved")
        assertTrue("The real file should be moved", File(moved, "real.txt").exists())
        assertFalse(
            "Nothing from behind the link may be written into the destination",
            File(moved, "link/payload.txt").exists()
        )
        assertLinkTargetIntact(linkTarget)
    }

    /**
     * `addToZip` skips a symlink on the same call. Nothing is deleted here, so the loss is a
     * disclosure rather than a destruction: a link into the user's own storage would put everything
     * behind it into an archive built from one folder and very likely shared — and a link pointing
     * back up its own tree would make the walk unbounded and the archive grow until the volume
     * filled.
     */
    @Test
    fun compress_symlinkedDirectory_archivesNeitherTheLinkNorWhatIsBehindIt() = runBlocking {
        val linkTarget = createLinkTarget()
        val folder = createFolderWithSymlinkTo("withLinkZipped", linkTarget)
        assumeTrue("Filesystem does not support symbolic links", folder != null)

        val progress = fileRepository.compressFiles(
            sources = listOf(FileItem.from(folder!!)),
            targetDir = targetDir.absolutePath,
            zipName = "archive.zip",
            allowedRoots = allowedRoots
        ).toList()

        val entries = ZipFile(File(progress.last().outputPath!!)).use { zip ->
            zip.entries().asSequence().map { it.name }.toSet()
        }
        assertEquals(
            "Only the selected folder and its real file belong in the archive",
            setOf("withLinkZipped/", "withLinkZipped/real.txt"),
            entries
        )
        assertLinkTargetIntact(linkTarget)
    }

    /**
     * A directory outside the tree the operation is aimed at, holding the one file every symlink
     * test above must find untouched afterwards.
     */
    private fun createLinkTarget(): File {
        val target = File(testDir, "linkTarget")
        target.mkdirs()
        createTestFile(target, "payload.txt", LINK_TARGET_CONTENT)
        return target
    }

    /**
     * `source/[name]/` holding `real.txt` and a `link` pointing at [linkTarget], or null when the
     * filesystem refused the link — the one condition these tests may skip on.
     */
    private fun createFolderWithSymlinkTo(name: String, linkTarget: File): File? {
        val folder = File(sourceDir, name)
        folder.mkdirs()
        createTestFile(folder, "real.txt", "content")
        val link = File(folder, "link")
        return try {
            Files.createSymbolicLink(link.toPath(), linkTarget.toPath())
            if (Files.isSymbolicLink(link.toPath())) folder else null
        } catch (_: Exception) {
            null
        }
    }

    /** Fails when the walk that met the link reached through it and changed what it points at. */
    private fun assertLinkTargetIntact(linkTarget: File) {
        val payload = File(linkTarget, "payload.txt")
        assertTrue("The symlink's target directory must survive", linkTarget.isDirectory)
        assertTrue("The file behind the symlink must survive", payload.exists())
        assertEquals(
            "The file behind the symlink must be untouched",
            LINK_TARGET_CONTENT,
            payload.readText()
        )
    }

    // endregion

    // region Helper Methods

    private fun createTestFile(dir: File, name: String, content: String): File {
        val file = File(dir, name)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    // endregion

    companion object {
        // An unpaired surrogate: the platform stores it as bytes that do not decode back to a
        // valid UTF-8 sequence, matching real-world names truncated mid-character.
        private const val MALFORMED_NAME = "broken\uD800name.txt"
        private const val MALFORMED_CONTENT = "content"

        // Distinct from every other fixture's content, so an assertion on it cannot pass against a
        // file some walk copied over the top of the one behind the link.
        private const val LINK_TARGET_CONTENT = "behind the link"
    }
}
