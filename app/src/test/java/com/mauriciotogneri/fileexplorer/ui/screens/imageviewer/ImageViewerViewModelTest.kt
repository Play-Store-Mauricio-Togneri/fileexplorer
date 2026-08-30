package com.mauriciotogneri.fileexplorer.ui.screens.imageviewer

import android.app.Application
import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.DeleteResult
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
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
import java.io.FileNotFoundException

@OptIn(ExperimentalCoroutinesApi::class)
class ImageViewerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var fileRepository: FileRepository
    private lateinit var tempDir: File

    private val testPath = "/storage/emulated/0/Pictures/photo.jpg"
    private val testSource = "folder"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        tempDir = File(System.getProperty("java.io.tmpdir"), "imageviewer_test_${System.nanoTime()}")
        tempDir.mkdirs()

        application = mockk(relaxed = true)
        fileRepository = mockk(relaxed = true)
        mockkObject(ErrorReporter)
        mockkObject(AnalyticsTracker)
        mockkObject(MediaStoreUtil)
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        every { AnalyticsTracker.trackImageViewerLoadError(any()) } just Runs
        coEvery { MediaStoreUtil.notifyDeleted(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(ErrorReporter)
        unmockkObject(AnalyticsTracker)
        unmockkObject(MediaStoreUtil)
        tempDir.deleteRecursively()
    }

    @Test
    fun `state exposes the file name of the path being viewed`() {
        assertEquals("photo.jpg", createViewModel().state.value.fileName)
    }

    @Test
    fun `a file that can no longer be read is not reported`() {
        // The exact failure reported by Crashlytics: Coil's decoder sniff is the first thing to open
        // the file and finds it gone — deleted or its volume unmounted while the viewer was on screen.
        // Environmental, and the error UI already covers it.
        val viewModel = createViewModel()

        viewModel.onImageLoadError(FileNotFoundException("$testPath: open failed: ENOENT"))

        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
        verify(exactly = 1) { AnalyticsTracker.trackImageViewerLoadError(testSource) }
    }

    @Test
    fun `content no decoder can turn into a bitmap is not reported`() {
        val viewModel = createViewModel()

        viewModel.onImageLoadError(IllegalStateException("BitmapFactory returned a null bitmap"))

        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
        verify(exactly = 1) { AnalyticsTracker.trackImageViewerLoadError(testSource) }
    }

    @Test
    fun `a corrupt animated file is not reported`() {
        // Below API 28 Coil's GifDecoder raises this instead of the IOException ImageDecoder raises
        // from API 28; the same bad file must stay quiet on either.
        val viewModel = createViewModel()

        viewModel.onImageLoadError(IllegalStateException("Failed to decode GIF."))

        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
        verify(exactly = 1) { AnalyticsTracker.trackImageViewerLoadError(testSource) }
    }

    @Test
    fun `a load failure for any other reason is still reported`() {
        // Keeps the suppressions above from widening: anything that is neither an unreadable file nor
        // undecodable content is a candidate app bug and has to reach Crashlytics.
        val viewModel = createViewModel()

        viewModel.onImageLoadError(IllegalStateException("boom"))

        verify(exactly = 1) { ErrorReporter.warning(any(), "image_viewer_load", any()) }
        verify(exactly = 1) { AnalyticsTracker.trackImageViewerLoadError(testSource) }
    }

    @Test
    fun `a reported load failure names no file anywhere in what is reported`() {
        // What reaches Crashlytics from a viewer failure is built from the image the user opened:
        // Coil wraps the decoder's own exception, whose message is the absolute path. recordException
        // transmits the message and every cause, so the whole reported object has to be name-free —
        // the scrub is what makes it so, and only this assertion keeps it applied here.
        val viewModel = createViewModel()
        val reported = slot<Throwable>()
        every { ErrorReporter.warning(capture(reported), any(), any()) } just Runs

        viewModel.onImageLoadError(
            IllegalStateException("decode failed", FileNotFoundException("$testPath: open failed: EACCES"))
        )

        val chain = generateSequence<Throwable>(reported.captured) { it.cause }.toList()
        assertEquals(1, chain.size)
        assertFalse(chain.single().message.orEmpty().contains("photo.jpg"))
        // The type still reaches the triager, or the report says nothing at all.
        assertTrue(chain.single().message.orEmpty().contains(IllegalStateException::class.java.name))
    }

    @Test
    fun `a load failure without a throwable is counted but not reported`() {
        val viewModel = createViewModel()

        viewModel.onImageLoadError(null)

        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
        verify(exactly = 1) { AnalyticsTracker.trackImageViewerLoadError(testSource) }
    }

    @Test
    fun `only the first load failure of a viewing is reported`() {
        // Coil re-invokes the error slot across recompositions; one viewing must not multiply into a
        // burst of identical reports.
        val viewModel = createViewModel()

        viewModel.onImageLoadError(IllegalStateException("boom"))
        viewModel.onImageLoadError(IllegalStateException("boom"))

        verify(exactly = 1) { ErrorReporter.warning(any(), "image_viewer_load", any()) }
        verify(exactly = 1) { AnalyticsTracker.trackImageViewerLoadError(testSource) }
    }

    @Test
    fun `deleting refuses a path a directory now occupies`() = runTest {
        // A recents or favorites entry keeps only the path it was stored with, and both repositories
        // re-validate it with exists() alone. If a directory carrying an image extension has taken
        // that path over, the item this screen resolves is that directory — and FileRepository.delete
        // walks it recursively, behind a confirm dialog that named a single file.
        val directory = File(tempDir, "photo.jpg").apply { mkdirs() }
        val child = File(directory, "inside.jpg").apply { writeText("data") }
        // Returning true is what makes this test earn its green: without the guard the delete would
        // succeed and the event below would be Finish.
        coEvery { fileRepository.delete(any()) } answers { DeleteResult(removedPaths = firstArg<List<FileItem>>().map { it.path }) }

        val viewModel = createViewModel(directory.absolutePath)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(ImageViewerUiEvent.ShowToast(R.string.delete_error), awaitItem())
        }
        coVerify(exactly = 0) { fileRepository.delete(any()) }
        // The repository is mocked, so this cannot witness a real recursive delete; it pins that
        // the ViewModel does no deleting of its own outside the repository.
        assertTrue("The ViewModel must not delete outside the repository", child.exists())
    }

    @Test
    fun `deleting still removes the single file the viewer was opened on`() = runTest {
        // The guard above must not cost the user the delete this screen exists to offer.
        val file = File(tempDir, "photo.jpg").apply { writeText("data") }
        coEvery { fileRepository.delete(any()) } answers { DeleteResult(removedPaths = firstArg<List<FileItem>>().map { it.path }) }

        val viewModel = createViewModel(file.absolutePath)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(ImageViewerUiEvent.Finish, awaitItem())
        }
        coVerify(exactly = 1) { fileRepository.delete(match { it.single().path == file.absolutePath }) }
    }

    @Test
    fun `deleting refuses a directory that replaced the file after the screen opened`() = runTest {
        // state.file is stat'd once, in init, and never refreshed, so it reports the file that was
        // there at open for as long as the viewer is on screen. FileRepository.delete re-resolves the
        // path and recurses on a live stat, so a guard reading that snapshot would pass a directory
        // straight into the tree walk.
        val file = File(tempDir, "photo.jpg").apply { writeText("data") }
        coEvery { fileRepository.delete(any()) } answers { DeleteResult(removedPaths = firstArg<List<FileItem>>().map { it.path }) }

        val viewModel = createViewModel(file.absolutePath)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse("The snapshot must start out as a file", viewModel.state.value.file!!.isDirectory)

        // What an external writer does to the path while the viewer sits open.
        file.delete()
        val directory = File(tempDir, "photo.jpg").apply { mkdirs() }
        val child = File(directory, "inside.jpg").apply { writeText("data") }

        viewModel.events.test {
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(ImageViewerUiEvent.ShowToast(R.string.delete_error), awaitItem())
        }
        coVerify(exactly = 0) { fileRepository.delete(any()) }
        assertTrue("The ViewModel must not delete outside the repository", child.exists())
    }

    private fun createViewModel(filePath: String = testPath) = ImageViewerViewModel(
        filePath = filePath,
        source = testSource,
        application = application,
        fileRepository = fileRepository,
        ioDispatcher = testDispatcher
    )
}
