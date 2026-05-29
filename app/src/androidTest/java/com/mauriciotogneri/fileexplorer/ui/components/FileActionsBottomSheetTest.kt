package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the folder per-file action sheet ([FileActionsBottomSheet]).
 *
 * Renders the real composable directly. [FileAction] referenced here is the sheet's own
 * `com.mauriciotogneri.fileexplorer.ui.components.FileAction` (same package, no import) — NOT the
 * unrelated `data.model.FileAction`. The sheet's `AnalyticsTracker` calls are null-safe no-ops
 * under test, so no Firebase init is required.
 */
@RunWith(AndroidJUnit4::class)
class FileActionsBottomSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun createTestFile(
        name: String = "document.txt",
        isDirectory: Boolean = false,
        size: Long = 1024L,
        mimeType: String = "text/plain",
        childCount: Int? = null
    ) = FileItem(
        path = "/storage/emulated/0/Documents/$name",
        name = name,
        isDirectory = isDirectory,
        size = size,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis(),
        mimeType = mimeType,
        childCount = childCount
    )

    private fun setSheet(
        file: FileItem,
        onAction: (FileAction) -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            FileExplorerTheme {
                // Production always opens this sheet in "icon" mode (long press handles selection).
                FileActionsBottomSheet(
                    file = file,
                    mode = "icon",
                    onAction = onAction,
                    onDismiss = onDismiss
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun assertActionDisplayed(resId: Int) {
        composeTestRule.onNodeWithText(context.getString(resId)).assertIsDisplayed()
    }

    private fun assertActionDoesNotExist(resId: Int) {
        composeTestRule.onNodeWithText(context.getString(resId)).assertDoesNotExist()
    }

    private fun clickAction(resId: Int) {
        composeTestRule.onNodeWithText(context.getString(resId)).performClick()
    }

    // ---------- Visibility ---------- \\

    @Test
    fun file_showsAllFileActions() {
        setSheet(createTestFile())

        assertActionDisplayed(R.string.action_select)
        assertActionDisplayed(R.string.action_share)
        assertActionDisplayed(R.string.action_open_with)
        assertActionDisplayed(R.string.action_move_to)
        assertActionDisplayed(R.string.action_copy_to)
        assertActionDisplayed(R.string.action_rename)
        assertActionDisplayed(R.string.action_compress)
        assertActionDisplayed(R.string.action_delete)
        assertActionDisplayed(R.string.action_info)
        // A regular (non-zip) file offers Compress, not Uncompress.
        assertActionDoesNotExist(R.string.action_uncompress)
    }

    @Test
    fun directory_hidesShareAndOpenWith() {
        setSheet(
            createTestFile(
                name = "MyFolder",
                isDirectory = true,
                size = 0L,
                mimeType = "",
                childCount = 3
            )
        )

        assertActionDoesNotExist(R.string.action_share)
        assertActionDoesNotExist(R.string.action_open_with)

        assertActionDisplayed(R.string.action_select)
        assertActionDisplayed(R.string.action_move_to)
        assertActionDisplayed(R.string.action_copy_to)
        assertActionDisplayed(R.string.action_rename)
        assertActionDisplayed(R.string.action_compress)
        assertActionDisplayed(R.string.action_delete)
        assertActionDisplayed(R.string.action_info)
    }

    @Test
    fun zipFile_showsUncompress_notCompress() {
        setSheet(createTestFile(name = "archive.zip", mimeType = "application/zip"))

        assertActionDisplayed(R.string.action_uncompress)
        assertActionDoesNotExist(R.string.action_compress)
    }

    @Test
    fun nonZipFile_showsCompress_notUncompress() {
        setSheet(createTestFile(name = "document.txt", mimeType = "text/plain"))

        assertActionDisplayed(R.string.action_compress)
        assertActionDoesNotExist(R.string.action_uncompress)
    }

    @Test
    fun directory_showsCompress_notUncompress() {
        setSheet(
            createTestFile(
                name = "MyFolder",
                isDirectory = true,
                size = 0L,
                mimeType = "",
                childCount = 0
            )
        )

        assertActionDisplayed(R.string.action_compress)
        assertActionDoesNotExist(R.string.action_uncompress)
    }

    // ---------- Callbacks ---------- \\

    @Test
    fun selectAction_invokesCallback() {
        var action: FileAction? = null
        setSheet(createTestFile(), onAction = { action = it })

        clickAction(R.string.action_select)

        assertEquals(FileAction.Select, action)
    }

    @Test
    fun shareAction_invokesCallback() {
        var action: FileAction? = null
        setSheet(createTestFile(), onAction = { action = it })

        clickAction(R.string.action_share)

        assertEquals(FileAction.Share, action)
    }

    @Test
    fun openWithAction_invokesCallback() {
        var action: FileAction? = null
        setSheet(createTestFile(), onAction = { action = it })

        clickAction(R.string.action_open_with)

        assertEquals(FileAction.OpenWith, action)
    }

    @Test
    fun moveToAction_invokesCallback() {
        var action: FileAction? = null
        setSheet(createTestFile(), onAction = { action = it })

        clickAction(R.string.action_move_to)

        assertEquals(FileAction.MoveTo, action)
    }

    @Test
    fun copyToAction_invokesCallback() {
        var action: FileAction? = null
        setSheet(createTestFile(), onAction = { action = it })

        clickAction(R.string.action_copy_to)

        assertEquals(FileAction.CopyTo, action)
    }

    @Test
    fun renameAction_invokesCallback() {
        var action: FileAction? = null
        setSheet(createTestFile(), onAction = { action = it })

        clickAction(R.string.action_rename)

        assertEquals(FileAction.Rename, action)
    }

    @Test
    fun compressAction_invokesCallback() {
        var action: FileAction? = null
        setSheet(createTestFile(name = "document.txt", mimeType = "text/plain"), onAction = { action = it })

        clickAction(R.string.action_compress)

        assertEquals(FileAction.Compress, action)
    }

    @Test
    fun uncompressAction_invokesCallback() {
        var action: FileAction? = null
        setSheet(createTestFile(name = "archive.zip", mimeType = "application/zip"), onAction = { action = it })

        clickAction(R.string.action_uncompress)

        assertEquals(FileAction.Uncompress, action)
    }

    @Test
    fun deleteAction_invokesCallback() {
        var action: FileAction? = null
        setSheet(createTestFile(), onAction = { action = it })

        clickAction(R.string.action_delete)

        assertEquals(FileAction.Delete, action)
    }

    @Test
    fun infoAction_invokesCallback() {
        var action: FileAction? = null
        setSheet(createTestFile(), onAction = { action = it })

        clickAction(R.string.action_info)

        assertEquals(FileAction.Info, action)
    }

    // ---------- Dismiss ---------- \\

    @Test
    fun dismiss_invokesOnDismiss() {
        var dismissed = false
        setSheet(createTestFile(), onDismiss = { dismissed = true })

        // The real sheet has no cancel button; back press routes to the sheet's onDismissRequest.
        Espresso.pressBack()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { dismissed }

        assertTrue(dismissed)
    }
}
