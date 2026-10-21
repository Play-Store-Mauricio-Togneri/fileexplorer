package com.mauriciotogneri.fileexplorer.ui.screens.home

import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [HomeUiState.isIdle] decides whether a rating request may appear over the home screen. */
class HomeUiStateTest {

    private val loaded = HomeUiState(isLoading = false)

    private val recentFile = RecentFile(
        path = "/storage/emulated/0/Documents/test.pdf",
        name = "test.pdf",
        mimeType = "application/pdf",
        lastOpenedTimestamp = 1_700_000_000_000L
    )

    private val favorite = Favorite(
        path = "/storage/emulated/0/Documents/notes.txt",
        name = "notes.txt",
        isDirectory = false,
        mimeType = "text/plain",
        favoritedTimestamp = 1_700_000_000_000L
    )

    private fun fileItem(name: String, mimeType: String) = FileItem(
        path = "/storage/emulated/0/Download/$name",
        name = name,
        isDirectory = false,
        size = 1024L,
        lastModified = 1000L,
        createdTime = 1000L,
        mimeType = mimeType
    )

    @Test
    fun `a loaded screen with nothing over it is idle`() {
        assertTrue(loaded.isIdle)
    }

    @Test
    fun `content on the screen does not make it busy`() {
        val state = loaded.copy(recentFiles = listOf(recentFile), favorites = listOf(favorite))

        assertTrue(state.isIdle)
    }

    @Test
    fun `a screen still loading is not idle`() {
        assertFalse(loaded.copy(isLoading = true).isIdle)
    }

    @Test
    fun `a recent file's sheet makes the screen busy`() {
        assertFalse(loaded.copy(selectedRecentFile = recentFile).isIdle)
    }

    @Test
    fun `confirming a recent file's removal makes the screen busy`() {
        assertFalse(loaded.copy(recentFileToDelete = recentFile).isIdle)
    }

    @Test
    fun `a favorite's sheet makes the screen busy`() {
        assertFalse(loaded.copy(selectedFavorite = favorite).isIdle)
    }

    @Test
    fun `confirming a favorite's removal makes the screen busy`() {
        assertFalse(loaded.copy(favoriteToDelete = favorite).isIdle)
    }

    @Test
    fun `the extract dialog makes the screen busy`() {
        assertFalse(loaded.copy(itemToUncompress = fileItem("archive.zip", "application/zip")).isIdle)
    }

    @Test
    fun `an extraction in progress makes the screen busy`() {
        val progress = UncompressProgress(
            currentFile = "file1.txt",
            extractedFiles = 1,
            totalFiles = 3,
            extractedBytes = 100L,
            totalBytes = 300L
        )

        assertFalse(loaded.copy(uncompressProgress = progress).isIdle)
    }

    @Test
    fun `an APK waiting on the install permission makes the screen busy`() {
        val apk = fileItem("app.apk", "application/vnd.android.package-archive")

        assertFalse(loaded.copy(pendingApkInstall = apk).isIdle)
    }
}
