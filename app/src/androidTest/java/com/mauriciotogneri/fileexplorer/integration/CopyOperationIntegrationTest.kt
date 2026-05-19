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
class CopyOperationIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var testDir: File
    private lateinit var sourceDir: File
    private lateinit var targetDir: File
    private lateinit var fileRepository: FileRepository
    private lateinit var storageRepository: StorageRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(context.cacheDir, "test_copy_${System.currentTimeMillis()}")
        testDir.mkdirs()

        sourceDir = File(testDir, "source")
        sourceDir.mkdirs()

        targetDir = File(testDir, "target")
        targetDir.mkdirs()

        fileRepository = FileRepository()
        storageRepository = StorageRepository(context)
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun copyOperation_pickerOpens_showsCopyToTitle() {
        val testFile = createTestFile(sourceDir, "test.txt", "content")
        val sourceItem = FileItem.from(testFile)
        val request = PickerRequest(
            items = listOf(sourceItem),
            mode = OperationMode.COPY
        )

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

        composeTestRule.onNodeWithText("Copy to").assertIsDisplayed()
    }

    @Test
    fun copyOperation_showsCopyHereButton() {
        val testFile = createTestFile(sourceDir, "test.txt", "content")
        val sourceItem = FileItem.from(testFile)
        val request = PickerRequest(
            items = listOf(sourceItem),
            mode = OperationMode.COPY
        )

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

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Copy here").assertIsDisplayed()
    }

    @Test
    fun copyOperation_confirmTriggersCallback_withTargetPath() {
        val testFile = createTestFile(sourceDir, "test.txt", "content")
        val sourceItem = FileItem.from(testFile)
        val request = PickerRequest(
            items = listOf(sourceItem),
            mode = OperationMode.COPY
        )
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
        composeTestRule.onNodeWithText("Copy here").performClick()

        assertTrue(confirmedPath != null)
    }

    @Test
    fun copyOperation_sameFolder_disablesCopyButton() {
        val testFile = createTestFile(sourceDir, "test.txt", "content")
        val sourceItem = FileItem.from(testFile)
        val request = PickerRequest(
            items = listOf(sourceItem),
            mode = OperationMode.COPY
        )

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

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Cannot copy to the same folder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Copy here").assertIsNotEnabled()
    }

    @Test
    fun copyOperation_navigateToFolder_enablesCopyButton() {
        val subFolder = File(sourceDir, "subfolder")
        subFolder.mkdirs()

        val testFile = createTestFile(sourceDir, "test.txt", "content")
        val sourceItem = FileItem.from(testFile)
        val request = PickerRequest(
            items = listOf(sourceItem),
            mode = OperationMode.COPY
        )

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

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("subfolder").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Cannot copy to the same folder").assertDoesNotExist()
        composeTestRule.onNodeWithText("Copy here").assertIsEnabled()
    }

    @Test
    fun copyOperation_folderIntoItself_showsRecursiveError() {
        val testFolder = File(sourceDir, "MyFolder")
        testFolder.mkdirs()
        val subFolder = File(testFolder, "SubFolder")
        subFolder.mkdirs()

        val folderItem = FileItem.from(testFolder)
        val request = PickerRequest(
            items = listOf(folderItem),
            mode = OperationMode.COPY
        )

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

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("MyFolder").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SubFolder").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Cannot copy a folder into itself").assertIsDisplayed()
        composeTestRule.onNodeWithText("Copy here").assertIsNotEnabled()
    }

    @Test
    fun copyOperation_pickerOverlay_slidesInFromBottom() {
        val testFile = createTestFile(sourceDir, "test.txt", "content")
        val sourceItem = FileItem.from(testFile)
        val request = PickerRequest(
            items = listOf(sourceItem),
            mode = OperationMode.COPY
        )
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

        composeTestRule.onNodeWithText("Copy to").assertDoesNotExist()

        showPicker = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Copy to").assertIsDisplayed()
    }

    @Test
    fun copyOperation_newFolderButton_isDisplayed() {
        val testFile = createTestFile(sourceDir, "test.txt", "content")
        val sourceItem = FileItem.from(testFile)
        val request = PickerRequest(
            items = listOf(sourceItem),
            mode = OperationMode.COPY
        )

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

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("New folder").assertIsDisplayed()
    }

    @Test
    fun copyOperation_multipleFiles_allSelectedForCopy() {
        val testFile1 = createTestFile(sourceDir, "file1.txt", "content1")
        val testFile2 = createTestFile(sourceDir, "file2.txt", "content2")
        val sourceItems = listOf(
            FileItem.from(testFile1),
            FileItem.from(testFile2)
        )
        val request = PickerRequest(
            items = sourceItems,
            mode = OperationMode.COPY
        )

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

        composeTestRule.onNodeWithText("Copy to").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cannot copy to the same folder").assertIsDisplayed()
    }

    private fun createTestFile(dir: File, name: String, content: String): File {
        val file = File(dir, name)
        file.writeText(content)
        return file
    }
}
