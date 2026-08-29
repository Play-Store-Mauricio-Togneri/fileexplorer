package com.mauriciotogneri.fileexplorer.integration

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.mauriciotogneri.fileexplorer.ui.screens.picker.DestinationPicker
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileOperationIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** User-facing assertions go through resources so they hold in every supported locale. */
    private fun string(@StringRes id: Int): String = testContext.getString(id)

    private val testContext = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var testDir: File
    private lateinit var sourceDir: File
    private lateinit var fileRepository: FileRepository
    private lateinit var storageRepository: StorageRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(context.cacheDir, "test_operation_${System.currentTimeMillis()}")
        testDir.mkdirs()

        sourceDir = File(testDir, "source")
        sourceDir.mkdirs()

        fileRepository = FileRepository()
        storageRepository = StorageRepository(FakeStorageSource(sourceDir))
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    // region Move Operation Tests

    @Test
    fun moveOperation_pickerOpens_showsMoveToTitle() {
        testPickerShowsTitle(OperationMode.MOVE, "Move to")
    }

    @Test
    fun moveOperation_showsMoveHereButton() {
        testShowsActionButton(OperationMode.MOVE, "Move here")
    }

    @Test
    fun moveOperation_confirmTriggersCallback_withTargetPath() {
        testConfirmTriggersCallback(OperationMode.MOVE, "Move here")
    }

    @Test
    fun moveOperation_sameFolder_disablesMoveButton() {
        testSameFolderDisablesButton(OperationMode.MOVE, "Cannot move to the same folder", "Move here")
    }

    @Test
    fun moveOperation_navigateToFolder_enablesMoveButton() {
        testNavigateToFolderEnablesButton(OperationMode.MOVE, "Cannot move to the same folder", "Move here")
    }

    @Test
    fun moveOperation_folderIntoItself_showsRecursiveError() {
        testFolderIntoItselfShowsError(OperationMode.MOVE, "Cannot move a folder into itself", "Move here")
    }

    @Test
    fun moveOperation_newFolderButton_isDisplayed() {
        testNewFolderButtonIsDisplayed(OperationMode.MOVE)
    }

    @Test
    fun moveOperation_multipleFiles_allSelectedForMove() {
        testMultipleFilesSelected(OperationMode.MOVE, "Move to", "Cannot move to the same folder")
    }

    // endregion

    // region Copy Operation Tests

    @Test
    fun copyOperation_pickerOpens_showsCopyToTitle() {
        testPickerShowsTitle(OperationMode.COPY, "Copy to")
    }

    @Test
    fun copyOperation_showsCopyHereButton() {
        testShowsActionButton(OperationMode.COPY, "Copy here")
    }

    @Test
    fun copyOperation_confirmTriggersCallback_withTargetPath() {
        testConfirmTriggersCallback(OperationMode.COPY, "Copy here")
    }

    @Test
    fun copyOperation_sameFolder_disablesCopyButton() {
        testSameFolderDisablesButton(OperationMode.COPY, "Cannot copy to the same folder", "Copy here")
    }

    @Test
    fun copyOperation_navigateToFolder_enablesCopyButton() {
        testNavigateToFolderEnablesButton(OperationMode.COPY, "Cannot copy to the same folder", "Copy here")
    }

    @Test
    fun copyOperation_folderIntoItself_showsRecursiveError() {
        testFolderIntoItselfShowsError(OperationMode.COPY, "Cannot copy a folder into itself", "Copy here")
    }

    @Test
    fun copyOperation_newFolderButton_isDisplayed() {
        testNewFolderButtonIsDisplayed(OperationMode.COPY)
    }

    @Test
    fun copyOperation_multipleFiles_allSelectedForCopy() {
        testMultipleFilesSelected(OperationMode.COPY, "Copy to", "Cannot copy to the same folder")
    }

    // endregion

    // region Folder Selection (no operation)

    /**
     * The startup-screen setting opens the same picker with no items and no [OperationMode], to
     * choose a folder rather than to move or copy into one. The bottom bar is covered in isolation
     * by `PickerBottomBarTest`; these drive the real [DestinationPicker], which is what the settings
     * screen actually shows.
     */
    @Test
    fun folderSelection_pickerOpens_showsSelectFolderTitle() {
        setDestinationPickerContent(folderSelectionRequest())

        composeTestRule.onNodeWithText(string(R.string.picker_title_select)).assertIsDisplayed()
    }

    @Test
    fun folderSelection_showsUseThisFolderButton() {
        setDestinationPickerContent(folderSelectionRequest())

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.picker_confirm_select)).assertIsDisplayed()
    }

    /**
     * Selecting lists read-only folders too, so offering to create one inside a folder that cannot
     * be written would fail — and the job here is to point at a folder that already exists.
     */
    @Test
    fun folderSelection_hidesNewFolderButton() {
        setDestinationPickerContent(folderSelectionRequest())

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.picker_new_folder)).assertDoesNotExist()
    }

    @Test
    fun folderSelection_confirm_returnsTheCurrentFolder() {
        var selectedPath: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                DestinationPicker(
                    request = folderSelectionRequest(),
                    sortMode = SortMode.NAME_ASC,
                    showHidden = false,
                    fileRepository = fileRepository,
                    storageRepository = storageRepository,
                    onConfirm = { selectedPath = it },
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.picker_confirm_select)).performClick()

        // The single fake storage root is navigated to on load, so it is what confirm returns.
        assertEquals(sourceDir.absolutePath, selectedPath)
    }

    /**
     * With no source items there is no destination conflict to report, so the storage root the
     * picker lands on is immediately a valid answer — unlike move and copy, which reject the folder
     * the selection already lives in.
     */
    @Test
    fun folderSelection_confirmIsEnabledOnTheStorageRoot() {
        setDestinationPickerContent(folderSelectionRequest())

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.picker_confirm_select)).assertIsEnabled()
    }

    private fun folderSelectionRequest() = PickerRequest(items = emptyList(), mode = null)

    // endregion

    // region Shared Test Implementations

    private fun testPickerShowsTitle(mode: OperationMode, expectedTitle: String) {
        val testFile = createTestFile(sourceDir, "test.txt", "content")
        val request = createRequest(testFile, mode)

        setDestinationPickerContent(request)

        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()
    }

    private fun testShowsActionButton(mode: OperationMode, expectedButtonText: String) {
        val testFile = createTestFile(sourceDir, "test.txt", "content")
        val request = createRequest(testFile, mode)

        setDestinationPickerContent(request)

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(expectedButtonText).assertIsDisplayed()
    }

    private fun testConfirmTriggersCallback(mode: OperationMode, buttonText: String) {
        val targetFolder = File(sourceDir, "target")
        targetFolder.mkdirs()

        val testFile = createTestFile(sourceDir, "test.txt", "content")
        val request = createRequest(testFile, mode)
        var confirmedPath: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                DestinationPicker(
                    request = request,
                    sortMode = SortMode.NAME_ASC,
                    showHidden = false,
                    fileRepository = fileRepository,
                    storageRepository = storageRepository,
                    onConfirm = { path -> confirmedPath = path },
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("target").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(buttonText).performClick()

        // The path the picker reports must be the folder that was navigated into, not the folder it
        // opened on: a picker that confirms its starting path lands every move and copy in the
        // wrong directory, and there is no undo.
        assertEquals(targetFolder.absolutePath, confirmedPath)
    }

    private fun testSameFolderDisablesButton(
        mode: OperationMode,
        errorMessage: String,
        buttonText: String
    ) {
        val testFile = createTestFile(sourceDir, "test.txt", "content")
        val request = createRequest(testFile, mode)

        setDestinationPickerContent(request)

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText(buttonText).assertIsNotEnabled()
    }

    private fun testNavigateToFolderEnablesButton(
        mode: OperationMode,
        errorMessage: String,
        buttonText: String
    ) {
        val subFolder = File(sourceDir, "subfolder")
        subFolder.mkdirs()

        val testFile = createTestFile(sourceDir, "test.txt", "content")
        val request = createRequest(testFile, mode)

        setDestinationPickerContent(request)

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("subfolder").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(errorMessage).assertDoesNotExist()
        composeTestRule.onNodeWithText(buttonText).assertIsEnabled()
    }

    private fun testFolderIntoItselfShowsError(
        mode: OperationMode,
        errorMessage: String,
        buttonText: String
    ) {
        val testFolder = File(sourceDir, "MyFolder")
        testFolder.mkdirs()
        val subFolder = File(testFolder, "SubFolder")
        subFolder.mkdirs()

        val folderItem = FileItem.from(testFolder)
        val request = PickerRequest(
            items = listOf(folderItem),
            mode = mode
        )

        setDestinationPickerContent(request)

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("MyFolder").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SubFolder").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText(buttonText).assertIsNotEnabled()
    }

    private fun testNewFolderButtonIsDisplayed(mode: OperationMode) {
        val testFile = createTestFile(sourceDir, "test.txt", "content")
        val request = createRequest(testFile, mode)

        setDestinationPickerContent(request)

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.picker_new_folder)).assertIsDisplayed()
    }

    private fun testMultipleFilesSelected(
        mode: OperationMode,
        expectedTitle: String,
        errorMessage: String
    ) {
        val testFile1 = createTestFile(sourceDir, "file1.txt", "content1")
        val testFile2 = createTestFile(sourceDir, "file2.txt", "content2")
        val sourceItems = listOf(
            FileItem.from(testFile1),
            FileItem.from(testFile2)
        )
        val request = PickerRequest(
            items = sourceItems,
            mode = mode
        )

        setDestinationPickerContent(request)

        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    // endregion

    // region Helpers

    private fun createTestFile(dir: File, name: String, content: String): File {
        val file = File(dir, name)
        file.writeText(content)
        return file
    }

    private fun createRequest(file: File, mode: OperationMode): PickerRequest {
        val sourceItem = FileItem.from(file)
        return PickerRequest(
            items = listOf(sourceItem),
            mode = mode
        )
    }

    private fun setDestinationPickerContent(request: PickerRequest) {
        composeTestRule.setContent {
            FileExplorerTheme {
                DestinationPicker(
                    request = request,
                    sortMode = SortMode.NAME_ASC,
                    showHidden = false,
                    fileRepository = fileRepository,
                    storageRepository = storageRepository,
                    onConfirm = {},
                    onCancel = {}
                )
            }
        }
    }

    // endregion
}
