package com.mauriciotogneri.fileexplorer.ui.screens.textviewer

import android.app.Application
import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.DeleteResult
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import com.mauriciotogneri.fileexplorer.util.ReviewPrompt
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * What [TextViewerViewModel] agrees to delete.
 *
 * The screen resolves the target itself, and `FileRepository.delete` then re-resolves it and decides
 * recursion from a live stat of its own — so what the user opened and what gets deleted are not the
 * same decision, and the two stats have to be taken at the same moment to agree.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TextViewerViewModelDeleteTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var fileRepository: FileRepository
    private lateinit var tempDir: File

    private val testSource = "recent"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        tempDir = File(System.getProperty("java.io.tmpdir"), "textviewer_delete_${System.nanoTime()}")
        tempDir.mkdirs()

        application = mockk(relaxed = true)
        fileRepository = mockk(relaxed = true)
        mockkObject(ErrorReporter)
        mockkObject(AnalyticsTracker)
        mockkObject(IntentUtil)
        mockkObject(MediaStoreUtil)
        mockkObject(ReviewPrompt)
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        every { ErrorReporter.setCount(any(), any()) } just Runs
        every { ErrorReporter.recordHeap() } just Runs
        every { AnalyticsTracker.trackTextViewerReadError(any()) } just Runs
        every { AnalyticsTracker.trackTextViewerOpened(any()) } just Runs
        every { AnalyticsTracker.trackFileOpened(any(), any(), any(), any()) } just Runs
        every { IntentUtil.trackRecentFile(any(), any()) } just Runs
        coEvery { MediaStoreUtil.notifyDeleted(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(ErrorReporter)
        unmockkObject(AnalyticsTracker)
        unmockkObject(IntentUtil)
        unmockkObject(MediaStoreUtil)
        unmockkObject(ReviewPrompt)
        tempDir.deleteRecursively()
    }

    @Test
    fun `deleting refuses a path a directory now occupies`() = runTest {
        // A recents or favorites entry keeps only the path it was stored with, and both repositories
        // re-validate it with exists() alone. Once a directory carrying a text extension occupies
        // that path the read fails, state.file stays null, and the fallback re-resolves the target
        // from disk as a directory — which FileRepository.delete walks recursively, behind a confirm
        // dialog that named a single file.
        val directory = File(tempDir, "notes.md").apply { mkdirs() }
        val child = File(directory, "inside.md").apply { writeText("keep me") }
        // Returning true is what makes this test earn its green: without the guard the delete would
        // succeed and the event below would be Finish.
        coEvery { fileRepository.delete(any()) } answers { DeleteResult(removedPaths = firstArg<List<FileItem>>().map { it.path }) }

        val viewModel = createViewModel(directory.absolutePath)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("The load must have failed for the fallback to be the resolver", viewModel.state.value.error)

        viewModel.events.test {
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(TextViewerUiEvent.ShowToast(R.string.delete_error), awaitItem())
        }
        coVerify(exactly = 0) { fileRepository.delete(any()) }
        // The repository is mocked, so this cannot witness a real recursive delete; it pins that
        // the ViewModel does no deleting of its own outside the repository.
        assertTrue("The ViewModel must not delete outside the repository", child.exists())
        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `deleting still removes the single file the viewer was opened on`() = runTest {
        // The guard above must not cost the user the delete this screen exists to offer.
        val file = File(tempDir, "notes.md").apply { writeText("hello") }
        coEvery { fileRepository.delete(any()) } answers { DeleteResult(removedPaths = firstArg<List<FileItem>>().map { it.path }) }

        val viewModel = createViewModel(file.absolutePath)
        testDispatcher.scheduler.advanceUntilIdle()
        // Opening the file counts on its own, before anything is deleted.
        verify(exactly = 1) { ReviewPrompt.onActionSucceeded() }

        viewModel.events.test {
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(TextViewerUiEvent.Finish, awaitItem())
        }
        coVerify(exactly = 1) { fileRepository.delete(match { it.single().path == file.absolutePath }) }
        // This screen tells the media index itself; without it the deleted file's row outlives
        // the file and other apps keep listing a document that is gone.
        coVerify(exactly = 1) { MediaStoreUtil.notifyDeleted(any(), listOf(file.absolutePath)) }
        verify(exactly = 2) { ReviewPrompt.onActionSucceeded() }
    }

    @Test
    fun `a delete the repository could not carry out blocks the rating ask`() = runTest {
        // No failure event reports this screen's deletes, so the ViewModel has to block the ask
        // itself, or a user could be sent to rate the app right after it failed them.
        val file = File(tempDir, "notes.md").apply { writeText("hello") }
        coEvery { fileRepository.delete(any()) } returns DeleteResult(failedCount = 1)

        val viewModel = createViewModel(file.absolutePath)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is TextViewerUiEvent.ShowToast)
        }
        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
        // The file is still there, so its media row must stay: reporting it deleted would hide a
        // document the user still has from other apps.
        coVerify(exactly = 0) { MediaStoreUtil.notifyDeleted(any(), any()) }
    }

    @Test
    fun `deleting refuses a directory that replaced the file after the screen opened`() = runTest {
        // A successful read leaves state.file holding the stat taken at open, and nothing refreshes it
        // for as long as the viewer is on screen. FileRepository.delete re-resolves the path and
        // recurses on a live stat, so a guard reading that snapshot would pass a directory straight
        // into the tree walk.
        val file = File(tempDir, "notes.md").apply { writeText("hello") }
        coEvery { fileRepository.delete(any()) } answers { DeleteResult(removedPaths = firstArg<List<FileItem>>().map { it.path }) }

        val viewModel = createViewModel(file.absolutePath)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse("The snapshot must start out as a file", viewModel.state.value.file!!.isDirectory)

        // What an external writer does to the path while the viewer sits open.
        file.delete()
        val directory = File(tempDir, "notes.md").apply { mkdirs() }
        val child = File(directory, "inside.md").apply { writeText("keep me") }

        viewModel.events.test {
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(TextViewerUiEvent.ShowToast(R.string.delete_error), awaitItem())
        }
        coVerify(exactly = 0) { fileRepository.delete(any()) }
        assertTrue("The ViewModel must not delete outside the repository", child.exists())
    }

    private fun createViewModel(filePath: String) = TextViewerViewModel(
        filePath = filePath,
        source = testSource,
        application = application,
        fileRepository = fileRepository,
        ioDispatcher = testDispatcher
    )
}
