package com.mauriciotogneri.fileexplorer.ui.screens.picker

import android.app.Application
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.OperationMode
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.model.StorageType
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import app.cash.turbine.test
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PickerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var fileRepository: FileRepository
    private lateinit var storageRepository: StorageRepository
    private lateinit var tempDir: File
    private lateinit var tempDir2: File

    private lateinit var internalStorage: StorageDevice
    private lateinit var sdCard: StorageDevice
    private lateinit var testSourceItems: List<FileItem>
    private lateinit var testFolders: List<FileItem>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        tempDir = File(System.getProperty("java.io.tmpdir"), "picker_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        tempDir2 = File(System.getProperty("java.io.tmpdir"), "picker_test2_${System.currentTimeMillis()}")
        tempDir2.mkdirs()

        val downloadsDir = File(tempDir, "Downloads")
        downloadsDir.mkdirs()
        val picturesDir = File(tempDir, "Pictures")
        picturesDir.mkdirs()
        val documentsDir = File(tempDir, "Documents")
        documentsDir.mkdirs()
        File(documentsDir, "file.txt").createNewFile()

        internalStorage = StorageDevice(
            path = tempDir.absolutePath,
            displayName = "Internal Storage",
            totalBytes = 64_000_000_000L,
            availableBytes = 32_000_000_000L,
            type = StorageType.INTERNAL
        )

        sdCard = StorageDevice(
            path = tempDir2.absolutePath,
            displayName = "SD Card",
            totalBytes = 32_000_000_000L,
            availableBytes = 16_000_000_000L,
            type = StorageType.SD_CARD
        )

        testSourceItems = listOf(
            FileItem(
                path = File(documentsDir, "file.txt").absolutePath,
                name = "file.txt",
                isDirectory = false,
                size = 1024L,
                lastModified = 1000L,
                createdTime = 1000L,
                mimeType = "text/plain",
                childCount = null
            )
        )

        testFolders = listOf(
            FileItem(
                path = downloadsDir.absolutePath,
                name = "Downloads",
                isDirectory = true,
                size = 0L,
                lastModified = 1000L,
                createdTime = 1000L,
                mimeType = "",
                childCount = 10
            ),
            FileItem(
                path = picturesDir.absolutePath,
                name = "Pictures",
                isDirectory = true,
                size = 0L,
                lastModified = 2000L,
                createdTime = 2000L,
                mimeType = "",
                childCount = 20
            )
        )

        application = mockk(relaxed = true)
        fileRepository = mockk()
        storageRepository = mockk()

        every { application.getString(R.string.validation_same_folder_move) } returns "Cannot move to the same folder"
        every { application.getString(R.string.validation_same_folder_copy) } returns "Cannot copy to the same folder"
        every { application.getString(R.string.validation_recursive_move) } returns "Cannot move a folder into itself"
        every { application.getString(R.string.validation_recursive_copy) } returns "Cannot copy a folder into itself"
        every { application.getString(R.string.validation_not_writable) } returns "Folder is not writable"

        mockkObject(AnalyticsTracker)
        mockkObject(ErrorReporter)
        every { AnalyticsTracker.trackDestinationPickerStorageSelected() } just Runs
        every { AnalyticsTracker.trackDestinationPickerFolderNavigated() } just Runs
        every { AnalyticsTracker.trackDestinationPickerNavigatedUp() } just Runs
        every { AnalyticsTracker.trackDestinationPickerFolderCreated() } just Runs
        every { ErrorReporter.error(any(), any()) } just Runs
        every { ErrorReporter.error(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        advanceAndWait()
        Dispatchers.resetMain()
        unmockkObject(AnalyticsTracker)
        unmockkObject(ErrorReporter)
        tempDir.deleteRecursively()
        tempDir2.deleteRecursively()
    }

    /**
     * Runs every coroutine to completion, background work included.
     *
     * This used to be `advanceUntilIdle()` around a `Thread.sleep(100)`, because storage and folder
     * loading ran on a hardcoded `Dispatchers.IO` that `setMain` cannot intercept — so the scheduler
     * had no idea it existed. That made every test in this file a race: slower than 100 ms under CI
     * load and it failed, faster and passing was never evidence the coroutine had actually run. The
     * ViewModel now takes [PickerViewModel.ioDispatcher], so [testDispatcher] owns that work too and
     * idle means idle.
     */
    private fun advanceAndWait() {
        testDispatcher.scheduler.advanceUntilIdle()
    }

    /**
     * [advanceAndWait] for a view model whose I/O runs on [ioDispatcher], a scheduler of its own:
     * storages first, then the root's listing and check, each hop to the I/O dispatcher and back
     * needing a turn of both schedulers.
     */
    private fun advanceAndWait(ioDispatcher: TestDispatcher) {
        repeat(3) {
            advanceAndWait()
            ioDispatcher.scheduler.advanceUntilIdle()
        }
        advanceAndWait()
    }

    private fun createViewModel(
        sourceItems: List<FileItem> = testSourceItems,
        operationMode: OperationMode? = OperationMode.MOVE,
        storages: List<StorageDevice> = listOf(internalStorage),
        folders: List<FileItem> = testFolders,
        ioDispatcher: CoroutineDispatcher = testDispatcher
    ): PickerViewModel {
        coEvery { storageRepository.getStorages() } returns storages
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns folders
        coEvery { fileRepository.listNames(any()) } returns emptySet()

        return PickerViewModel(
            application = application,
            fileRepository = fileRepository,
            storageRepository = storageRepository,
            sourceItems = sourceItems,
            operationMode = operationMode,
            sortMode = SortMode.NAME_ASC,
            showHidden = false,
            ioDispatcher = ioDispatcher
        )
    }

    @Test
    fun `single storage navigates directly to storage root`() = runTest {
        val viewModel = createViewModel(storages = listOf(internalStorage))
        advanceAndWait()

        assertEquals(internalStorage.path, viewModel.currentPath.value)
    }

    @Test
    fun `navigateToFolder updates current path`() = runTest {
        val viewModel = createViewModel()
        advanceAndWait()

        viewModel.navigateToFolder(testFolders[0])
        advanceAndWait()

        assertEquals(testFolders[0].path, viewModel.currentPath.value)
    }

    @Test
    fun `navigateUp from subfolder goes to parent`() = runTest {
        val subFolder = File(tempDir, "Documents/SubFolder")
        subFolder.mkdirs()

        val viewModel = createViewModel()
        advanceAndWait()

        viewModel.navigateToPath(subFolder.absolutePath)
        advanceAndWait()

        val result = viewModel.navigateUp()
        advanceAndWait()

        assertTrue(result)
        assertEquals(File(tempDir, "Documents").absolutePath, viewModel.currentPath.value)
    }

    @Test
    fun `navigateUp from storage root with single storage returns false`() = runTest {
        val viewModel = createViewModel(storages = listOf(internalStorage))
        advanceAndWait()

        val result = viewModel.navigateUp()

        assertFalse(result)
    }

    @Test
    fun `showCreateFolderDialog sets state to true`() = runTest {
        val viewModel = createViewModel()
        advanceAndWait()

        assertFalse(viewModel.showCreateFolderDialog.value)

        viewModel.showCreateFolderDialog()

        assertTrue(viewModel.showCreateFolderDialog.value)
    }

    @Test
    fun `dismissCreateFolderDialog sets state to false`() = runTest {
        val viewModel = createViewModel()
        advanceAndWait()

        viewModel.showCreateFolderDialog()
        viewModel.dismissCreateFolderDialog()

        assertFalse(viewModel.showCreateFolderDialog.value)
    }

    @Test
    fun `storages are loaded on init`() = runTest {
        val viewModel = createViewModel(storages = listOf(internalStorage, sdCard))
        advanceAndWait()

        assertEquals(2, viewModel.storages.value.size)
    }

    @Test
    fun `navigateToPath updates current path`() = runTest {
        val viewModel = createViewModel()
        advanceAndWait()

        val newPath = File(tempDir, "Downloads").absolutePath
        viewModel.navigateToPath(newPath)
        advanceAndWait()

        assertEquals(newPath, viewModel.currentPath.value)
    }

    @Test
    fun `navigateToStorage sets current path`() = runTest {
        val viewModel = createViewModel(storages = listOf(internalStorage, sdCard))
        advanceAndWait()

        viewModel.navigateToStorage(sdCard)
        advanceAndWait()

        assertEquals(sdCard.path, viewModel.currentPath.value)
    }

    // ==================== Folder selection (null operation mode) ====================

    /**
     * Choosing a folder to open on startup only needs to list it, so a read-only folder stays
     * offered. Move and copy keep the writable filter, which the neighbouring test pins.
     */
    @Test
    fun `folder selection lists read-only folders`() = runTest {
        val readOnly = File(tempDir, "ReadOnly").apply { mkdirs() }
        // Guarded on the permission the filter reads, not on setWritable's return value: run as
        // root the chmod succeeds and reports true while the folder stays writable, and the test
        // would then assert over a writable folder and could no longer fail.
        readOnly.setWritable(false, false)
        assumeTrue("Filesystem does not enforce directory write permission", !readOnly.canWrite())
        val readOnlyItem = folderItem(readOnly)

        val viewModel = createViewModel(
            sourceItems = emptyList(),
            operationMode = null,
            folders = listOf(readOnlyItem)
        )
        advanceAndWait()

        assertEquals(listOf(readOnlyItem), viewModel.folders.value)
    }

    @Test
    fun `move hides read-only folders`() = runTest {
        val readOnly = File(tempDir, "ReadOnlyMove").apply { mkdirs() }
        readOnly.setWritable(false, false)
        assumeTrue("Filesystem does not enforce directory write permission", !readOnly.canWrite())

        val viewModel = createViewModel(
            operationMode = OperationMode.MOVE,
            folders = listOf(folderItem(readOnly))
        )
        advanceAndWait()

        assertTrue(viewModel.folders.value.isEmpty())
    }

    /**
     * No source items means no destination conflict to report, so any listed folder is a valid
     * answer. Asserts the error and path inputs `isValidDestination` combines rather than the flow
     * itself, which is `WhileSubscribed` and so emits its initial value before recomputing; its
     * third input, the pending writability check, is never raised without source items.
     */
    @Test
    fun `folder selection reports no validation error`() = runTest {
        val viewModel = createViewModel(sourceItems = emptyList(), operationMode = null)
        advanceAndWait()

        assertNull(viewModel.validationError.value)
        assertEquals(internalStorage.path, viewModel.currentPath.value)
    }

    // ==================== Validation over a multi-item selection ====================

    /**
     * Every selected item is checked, not just the first. The folder is listed second on purpose:
     * a check that only looked at `sourceItems.first()` would see the plain file, find nothing
     * wrong, and let the user confirm a copy into the folder's own subfolder — which recurses into
     * itself, and as a move then deletes the source.
     */
    @Test
    fun `a destination inside the second selected folder is refused for move and copy`() = runTest {
        val folder = File(tempDir, "Documents/MyFolder").apply { mkdirs() }
        val subFolder = File(folder, "SubFolder").apply { mkdirs() }
        val selection = listOf(testSourceItems.single(), folderItem(folder))

        listOf(
            OperationMode.MOVE to "Cannot move a folder into itself",
            OperationMode.COPY to "Cannot copy a folder into itself"
        ).forEach { (mode, expected) ->
            val viewModel = createViewModel(sourceItems = selection, operationMode = mode)
            advanceAndWait()

            viewModel.navigateToPath(subFolder.absolutePath)
            advanceAndWait()

            assertEquals("$mode into a subfolder of the selection", expected, viewModel.validationError.value)
        }
    }

    @Test
    fun `the source folder of the second selected item is refused as a destination`() = runTest {
        val elsewhere = File(tempDir, "Pictures/photo.jpg").apply { createNewFile() }
        val selection = listOf(
            testSourceItems.single(),
            FileItem(
                path = elsewhere.absolutePath,
                name = elsewhere.name,
                isDirectory = false,
                size = 0L,
                lastModified = 1000L,
                createdTime = 1000L,
                mimeType = "image/jpeg",
                childCount = null
            )
        )
        val viewModel = createViewModel(sourceItems = selection, operationMode = OperationMode.COPY)
        advanceAndWait()

        viewModel.navigateToPath(File(tempDir, "Pictures").absolutePath)
        advanceAndWait()

        assertEquals("Cannot copy to the same folder", viewModel.validationError.value)
    }

    @Test
    fun `a destination clear of every selected item is accepted`() = runTest {
        val folder = File(tempDir, "Documents/MyFolder").apply { mkdirs() }
        val selection = listOf(testSourceItems.single(), folderItem(folder))
        val viewModel = createViewModel(sourceItems = selection, operationMode = OperationMode.COPY)
        advanceAndWait()

        viewModel.navigateToPath(File(tempDir, "Downloads").absolutePath)
        advanceAndWait()

        assertNull(viewModel.validationError.value)
    }

    // ==================== Writability check ====================

    /**
     * The writability check is file-system I/O and runs on the I/O dispatcher, which this test
     * holds back on a scheduler of its own. Until it answers, confirm must stay disabled; a check
     * made on the main thread would already have answered inside `navigateToPath`.
     */
    @Test
    fun `confirm stays disabled while the writability check is pending`() = runTest {
        // A scheduler of its own: without one, a new test dispatcher joins the one `setMain`
        // installed, and advancing the main thread would run the check too.
        val ioDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
        val viewModel = createViewModel(ioDispatcher = ioDispatcher)
        backgroundScope.launch { viewModel.isValidDestination.collect {} }
        advanceAndWait(ioDispatcher)
        assertTrue("the storage root settles as a valid destination", viewModel.isValidDestination.value)

        viewModel.navigateToPath(File(tempDir, "Downloads").absolutePath)
        advanceAndWait()

        assertFalse(viewModel.isValidDestination.value)
        assertNull(viewModel.validationError.value)

        ioDispatcher.scheduler.advanceUntilIdle()
        advanceAndWait()

        assertTrue(viewModel.isValidDestination.value)
    }

    /**
     * Folder A's check has answered on the I/O dispatcher, but the answer has not reached the main
     * thread when the user opens B. Delivered anyway, A's "valid" would enable confirm on B — here
     * a folder inside the selection, so a move into itself.
     */
    @Test
    fun `a superseded check cannot enable confirm on the next folder`() = runTest {
        val folder = File(tempDir, "Documents/MyFolder").apply { mkdirs() }
        val subFolder = File(folder, "SubFolder").apply { mkdirs() }
        val ioDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
        val viewModel = createViewModel(
            sourceItems = listOf(folderItem(folder)),
            operationMode = OperationMode.MOVE,
            ioDispatcher = ioDispatcher
        )
        backgroundScope.launch { viewModel.isValidDestination.collect {} }
        advanceAndWait(ioDispatcher)
        assertTrue("the storage root settles as a valid destination", viewModel.isValidDestination.value)

        viewModel.navigateToPath(File(tempDir, "Downloads").absolutePath)
        advanceAndWait()
        ioDispatcher.scheduler.advanceUntilIdle()
        viewModel.navigateToPath(subFolder.absolutePath)
        advanceAndWait()

        assertFalse(viewModel.isValidDestination.value)

        ioDispatcher.scheduler.advanceUntilIdle()
        advanceAndWait()

        assertEquals("Cannot move a folder into itself", viewModel.validationError.value)
        assertFalse(viewModel.isValidDestination.value)
    }

    @Test
    fun `a read-only destination is refused`() = runTest {
        val readOnly = File(tempDir, "ReadOnlyTarget").apply { mkdirs() }
        readOnly.setWritable(false, false)
        assumeTrue("Filesystem does not enforce directory write permission", !readOnly.canWrite())

        val viewModel = createViewModel()
        advanceAndWait()

        viewModel.navigateToPath(readOnly.absolutePath)
        advanceAndWait()

        assertEquals("Folder is not writable", viewModel.validationError.value)
    }

    // ==================== Create folder ====================

    /**
     * The listing is mocked to the two writable folders, so every other name here can only come
     * from the full name listing: a file, a hidden folder, and a folder the listing left out.
     */
    @Test
    fun `existing names cover every entry in the folder, not just the listed ones`() = runTest {
        val viewModel = createViewModel()
        coEvery { fileRepository.listNames(internalStorage.path) } returns
            setOf("Downloads", "Pictures", "Documents", "notes.txt", ".hidden")
        advanceAndWait()

        assertEquals(
            setOf("Downloads", "Pictures", "Documents", "notes.txt", ".hidden"),
            viewModel.getExistingNames()
        )
    }

    @Test
    fun `failed folder creation shows an error and stays in the current folder`() = runTest {
        coEvery { fileRepository.createFolder(any(), any()) } returns false

        val viewModel = createViewModel()
        advanceAndWait()

        viewModel.events.test {
            viewModel.createFolder("New")
            advanceAndWait()

            assertEquals(PickerUiEvent.ShowToast(R.string.create_error), awaitItem())
        }
        assertEquals(internalStorage.path, viewModel.currentPath.value)
        assertFalse(viewModel.showCreateFolderDialog.value)
    }

    private fun folderItem(folder: File) = FileItem(
        path = folder.absolutePath,
        name = folder.name,
        isDirectory = true,
        size = 0L,
        lastModified = 1000L,
        createdTime = 1000L,
        mimeType = "",
        childCount = 0
    )
}
