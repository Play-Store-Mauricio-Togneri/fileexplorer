package com.mauriciotogneri.fileexplorer.integration

import androidx.compose.ui.test.assertIsDisplayed
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
class PickerNavigationIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var testDir: File
    private lateinit var fileRepository: FileRepository
    private lateinit var storageRepository: StorageRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(context.cacheDir, "test_nav_${System.currentTimeMillis()}")
        testDir.mkdirs()

        fileRepository = FileRepository()
        storageRepository = StorageRepository(FakeStorageSource(testDir))
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun picker_navigateIntoFolder_showsContents() {
        val parentFolder = File(testDir, "Parent")
        parentFolder.mkdirs()
        val childFolder = File(parentFolder, "Child")
        childFolder.mkdirs()
        val grandchildFolder = File(childFolder, "Grandchild")
        grandchildFolder.mkdirs()

        val testFile = createTestFile(testDir, "source.txt", "content")
        val sourceItem = FileItem.from(testFile)
        val request = PickerRequest(
            items = listOf(sourceItem),
            mode = OperationMode.MOVE
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
        composeTestRule.onNodeWithText("Parent").assertIsDisplayed()

        composeTestRule.onNodeWithText("Parent").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Child").assertIsDisplayed()

        composeTestRule.onNodeWithText("Child").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Grandchild").assertIsDisplayed()
    }

    @Test
    fun picker_showsOnlyFolders_notFiles() {
        val folder = File(testDir, "OnlyFolder")
        folder.mkdirs()
        createTestFile(testDir, "hidden_file.txt", "should not show")

        val testFile = createTestFile(testDir, "external.txt", "x")
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

        composeTestRule.onNodeWithText("OnlyFolder").assertIsDisplayed()
        composeTestRule.onNodeWithText("hidden_file.txt").assertDoesNotExist()
    }

    @Test
    fun picker_multipleFolders_displaysAll() {
        File(testDir, "Alpha").mkdirs()
        File(testDir, "Beta").mkdirs()
        File(testDir, "Gamma").mkdirs()

        val testFile = createTestFile(testDir, "source.txt", "content")
        val sourceItem = FileItem.from(testFile)
        val request = PickerRequest(
            items = listOf(sourceItem),
            mode = OperationMode.MOVE
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

        composeTestRule.onNodeWithText("Alpha").assertIsDisplayed()
        composeTestRule.onNodeWithText("Beta").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gamma").assertIsDisplayed()
    }

    @Test
    fun picker_emptyFolder_showsEmptyList() {
        val emptyFolder = File(testDir, "EmptyFolder")
        emptyFolder.mkdirs()

        val testFile = createTestFile(testDir, "source.txt", "content")
        val sourceItem = FileItem.from(testFile)
        val request = PickerRequest(
            items = listOf(sourceItem),
            mode = OperationMode.MOVE
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
        composeTestRule.onNodeWithText("EmptyFolder").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("New folder").assertIsDisplayed()
    }

    @Test
    fun picker_sortedByName_displaysInOrder() {
        File(testDir, "Zebra").mkdirs()
        File(testDir, "Apple").mkdirs()
        File(testDir, "Mango").mkdirs()

        val testFile = createTestFile(testDir, "source.txt", "content")
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

        composeTestRule.onNodeWithText("Apple").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mango").assertIsDisplayed()
        composeTestRule.onNodeWithText("Zebra").assertIsDisplayed()
    }

    @Test
    fun picker_hiddenFolder_notShownWhenShowHiddenFalse() {
        File(testDir, ".hidden").mkdirs()
        File(testDir, "visible").mkdirs()

        val testFile = createTestFile(testDir, "source.txt", "content")
        val sourceItem = FileItem.from(testFile)
        val request = PickerRequest(
            items = listOf(sourceItem),
            mode = OperationMode.MOVE
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

        composeTestRule.onNodeWithText("visible").assertIsDisplayed()
        composeTestRule.onNodeWithText(".hidden").assertDoesNotExist()
    }

    @Test
    fun picker_hiddenFolder_shownWhenShowHiddenTrue() {
        File(testDir, ".hidden").mkdirs()
        File(testDir, "visible").mkdirs()

        val testFile = createTestFile(testDir, "source.txt", "content")
        val sourceItem = FileItem.from(testFile)
        val request = PickerRequest(
            items = listOf(sourceItem),
            mode = OperationMode.MOVE
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                DestinationPicker(
                    request = request,
                    sortMode = SortMode.NAME_ASC,
                    showHidden = true,
                    fileRepository = fileRepository,
                    storageRepository = storageRepository,
                    onConfirm = {},
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("visible").assertIsDisplayed()
        composeTestRule.onNodeWithText(".hidden").assertIsDisplayed()
    }

    @Test
    fun picker_confirmReturnsCorrectPath() {
        val targetFolder = File(testDir, "TargetFolder")
        targetFolder.mkdirs()

        val testFile = createTestFile(testDir, "source.txt", "content")
        val sourceItem = FileItem.from(testFile)
        val request = PickerRequest(
            items = listOf(sourceItem),
            mode = OperationMode.MOVE
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
        composeTestRule.onNodeWithText("TargetFolder").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Move here").performClick()

        assertEquals(targetFolder.absolutePath, confirmedPath)
    }

    @Test
    fun picker_deepNavigation_confirmsCorrectPath() {
        val level1 = File(testDir, "Level1")
        level1.mkdirs()
        val level2 = File(level1, "Level2")
        level2.mkdirs()
        val level3 = File(level2, "Level3")
        level3.mkdirs()

        val testFile = createTestFile(testDir, "source.txt", "content")
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
        composeTestRule.onNodeWithText("Level1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Level2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Level3").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Copy here").performClick()

        assertEquals(level3.absolutePath, confirmedPath)
    }

    private fun createTestFile(dir: File, name: String, content: String): File {
        dir.mkdirs()
        val file = File(dir, name)
        file.writeText(content)
        return file
    }
}
