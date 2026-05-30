package com.mauriciotogneri.fileexplorer.integration

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.OperationMode
import com.mauriciotogneri.fileexplorer.data.model.PickerRequest
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.testutil.FakeStorageSource
import com.mauriciotogneri.fileexplorer.testutil.MultiStorageFakeStorageSource
import com.mauriciotogneri.fileexplorer.ui.screens.picker.DestinationPicker
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Stage 6 (Point 6): end-to-end coverage of the picker "New Folder" flow and multi-storage
 * switching (existing PickerNavigationIntegrationTest only checks folder navigation + confirm).
 *
 * Real-wiring: the real [DestinationPicker] + [PickerViewModel] + [FileRepository] operate over a
 * temp dir exposed through `StorageRepository(FakeStorageSource(testDir))` (single storage) or
 * `MultiStorageFakeStorageSource` (two roots). `createFolder` does not check allowed-roots, so the
 * folder is really created on disk under the temp dir.
 *
 * Note: per `PickerViewModel.createFolder`, a successful create navigates INTO the new folder, so it
 * does not reappear in the parent list — coverage asserts on-disk creation + the confirm round-trip.
 */
@RunWith(AndroidJUnit4::class)
class PickerCreateFolderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fileRepository = FileRepository()

    private lateinit var testDir: File
    private lateinit var sourceDir: File
    private lateinit var sourceItem: FileItem

    @Before
    fun setUp() {
        val stamp = System.currentTimeMillis()
        testDir = File(context.cacheDir, "test_picker_$stamp").apply { mkdirs() }
        // Source lives OUTSIDE testDir so targets under testDir are valid destinations.
        sourceDir = File(context.cacheDir, "test_picker_src_$stamp").apply { mkdirs() }
        val sourceFile = File(sourceDir, "source.txt").apply { writeText("x") }
        sourceItem = FileItem.from(sourceFile)
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
        sourceDir.deleteRecursively()
    }

    // ==================== New Folder ====================

    @Test
    fun newFolder_dialogOpens_onButtonTap() {
        renderPicker(singleStorage())
        openNewFolderDialog()

        // Assert via the dialog's Create button: the title string (action_create_folder) shares its
        // display text "New folder" with the bottom-bar button, so it is not a unique anchor.
        composeTestRule.onNodeWithText(string(R.string.dialog_create)).assertIsDisplayed()
    }

    @Test
    fun newFolder_validName_createsOnDisk() {
        renderPicker(singleStorage())
        openNewFolderDialog()

        typeText("MyNewFolder")
        composeTestRule.onNodeWithText(string(R.string.dialog_create)).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { File(testDir, "MyNewFolder").isDirectory }
        assertTrue(File(testDir, "MyNewFolder").isDirectory)
    }

    @Test
    fun newFolder_duplicateName_showsError() {
        File(testDir, "Existing").mkdirs()
        renderPicker(singleStorage())
        waitForText("Existing") // ensure the folder list (existing names) is loaded
        openNewFolderDialog()

        typeText("Existing")

        waitForText(string(R.string.error_name_exists))
        composeTestRule.onNodeWithText(string(R.string.error_name_exists)).assertIsDisplayed()
    }

    @Test
    fun newFolder_cancel_doesNotCreate() {
        renderPicker(singleStorage())
        openNewFolderDialog()

        typeText("ShouldNotExist")
        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).performClick()
        composeTestRule.waitForIdle()

        assertFalse(File(testDir, "ShouldNotExist").exists())
    }

    @Test
    fun newFolder_thenConfirm_returnsNewFolderPath() {
        var confirmedPath: String? = null
        renderPicker(singleStorage(), mode = OperationMode.MOVE, onConfirm = { confirmedPath = it })
        openNewFolderDialog()

        typeText("RoundTrip")
        composeTestRule.onNodeWithText(string(R.string.dialog_create)).performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { File(testDir, "RoundTrip").isDirectory }

        // Create navigates into the new folder, which becomes a valid destination -> confirm enabled.
        waitUntilEnabled(R.string.picker_confirm_move)
        composeTestRule.onNodeWithText(string(R.string.picker_confirm_move)).performClick()

        assertEquals(File(testDir, "RoundTrip").absolutePath, confirmedPath)
    }

    // ==================== Storage switching ====================

    @Test
    fun singleStorage_skipsSelector_showsFolders() {
        File(testDir, "DirectFolder").mkdirs()
        renderPicker(singleStorage())

        waitForText("DirectFolder")
        composeTestRule.onNodeWithText("DirectFolder").assertIsDisplayed()
        // Bottom bar (new folder) only renders in folder view, i.e. the selector was skipped.
        composeTestRule.onNodeWithText(string(R.string.picker_new_folder)).assertIsDisplayed()
    }

    @Test
    fun multipleStorages_showsStorageSelector() {
        renderPicker(multiStorage())

        waitForText(PRIMARY)
        composeTestRule.onNodeWithText(PRIMARY).assertIsDisplayed()
        composeTestRule.onNodeWithText(SECONDARY).assertIsDisplayed()
    }

    @Test
    fun selectStorage_navigatesIntoIt_showsItsFolders() {
        renderPicker(multiStorage())
        waitForText(SECONDARY)
        composeTestRule.onNodeWithText(SECONDARY).performClick()

        waitForText("FolderInB")
        composeTestRule.onNodeWithText("FolderInB").assertIsDisplayed()
        composeTestRule.onNodeWithText("FolderInA").assertDoesNotExist()
    }

    @Test
    fun navigateUpFromStorageRoot_returnsToStorageSelector() {
        renderPicker(multiStorage())
        waitForText(PRIMARY)
        composeTestRule.onNodeWithText(PRIMARY).performClick()
        waitForText("FolderInA")

        // Back from a storage root with multiple storages returns to the selector.
        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()

        waitForText(SECONDARY)
        composeTestRule.onNodeWithText(PRIMARY).assertIsDisplayed()
        composeTestRule.onNodeWithText(SECONDARY).assertIsDisplayed()
    }

    // ==================== Helpers ====================

    private fun singleStorage() = StorageRepository(FakeStorageSource(testDir))

    private fun multiStorage(): StorageRepository {
        val storageA = File(testDir, "storageA").apply { mkdirs() }
        val storageB = File(testDir, "storageB").apply { mkdirs() }
        File(storageA, "FolderInA").mkdirs()
        File(storageB, "FolderInB").mkdirs()
        return StorageRepository(
            MultiStorageFakeStorageSource.of(storageA to PRIMARY, storageB to SECONDARY)
        )
    }

    private fun renderPicker(
        storageRepository: StorageRepository,
        mode: OperationMode = OperationMode.MOVE,
        onConfirm: (String) -> Unit = {}
    ) {
        val request = PickerRequest(items = listOf(sourceItem), mode = mode)
        composeTestRule.setContent {
            FileExplorerTheme {
                DestinationPicker(
                    request = request,
                    sortMode = SortMode.NAME_ASC,
                    showHidden = false,
                    fileRepository = fileRepository,
                    storageRepository = storageRepository,
                    onConfirm = onConfirm,
                    onCancel = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun openNewFolderDialog() {
        waitForText(string(R.string.picker_new_folder))
        composeTestRule.onNodeWithText(string(R.string.picker_new_folder)).performClick()
        // Wait on the dialog's unique Create button (the title text collides with the bottom bar).
        waitForText(string(R.string.dialog_create))
    }

    private fun typeText(text: String) {
        composeTestRule.onNode(hasSetTextAction()).performTextInput(text)
        composeTestRule.waitForIdle()
    }

    private fun waitUntilEnabled(@StringRes textRes: Int) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeTestRule.onNodeWithText(string(textRes)).assertIsEnabled()
                true
            }.getOrDefault(false)
        }
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        const val PRIMARY = "Primary Storage"
        const val SECONDARY = "Secondary Storage"
    }
}
