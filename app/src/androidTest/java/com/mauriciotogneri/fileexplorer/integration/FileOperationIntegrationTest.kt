package com.mauriciotogneri.fileexplorer.integration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.OperationMode
import com.mauriciotogneri.fileexplorer.data.model.PickerRequest
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.source.AndroidStorageSource
import com.mauriciotogneri.fileexplorer.ui.screens.picker.DestinationPicker
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileOperationIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
        storageRepository = StorageRepository(AndroidStorageSource(context))
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
    fun moveOperation_pickerOverlay_slidesInFromBottom() {
        testPickerOverlaySlidesIn(OperationMode.MOVE, "Move to")
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
    fun copyOperation_pickerOverlay_slidesInFromBottom() {
        testPickerOverlaySlidesIn(OperationMode.COPY, "Copy to")
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
        composeTestRule.onNodeWithText(buttonText).performClick()

        assertTrue(confirmedPath != null)
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

    private fun testPickerOverlaySlidesIn(mode: OperationMode, expectedTitle: String) {
        val testFile = createTestFile(sourceDir, "test.txt", "content")
        val request = createRequest(testFile, mode)
        var showPicker by mutableStateOf(false)

        composeTestRule.setContent {
            FileExplorerTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = showPicker,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it }
                    ) {
                        DestinationPicker(
                            request = request,
                            sortMode = SortMode.NAME_ASC,
                            showHidden = false,
                            fileRepository = fileRepository,
                            storageRepository = storageRepository,
                            onConfirm = {},
                            onCancel = { showPicker = false }
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithText(expectedTitle).assertDoesNotExist()

        showPicker = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()
    }

    private fun testNewFolderButtonIsDisplayed(mode: OperationMode) {
        val testFile = createTestFile(sourceDir, "test.txt", "content")
        val request = createRequest(testFile, mode)

        setDestinationPickerContent(request)

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("New folder").assertIsDisplayed()
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
