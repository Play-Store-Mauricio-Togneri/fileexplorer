package com.mauriciotogneri.fileexplorer.ui.screens.pdfviewer

import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.DeleteResult
import com.mauriciotogneri.fileexplorer.data.source.FakePdfDocumentOpener
import com.mauriciotogneri.fileexplorer.data.util.DeleteFailure
import com.mauriciotogneri.fileexplorer.data.util.ERRNO_UNKNOWN
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * What [PdfViewerViewModel] agrees to delete — the same guard the image and text viewers carry.
 *
 * The confirm dialog names one file, but `FileRepository.delete` re-resolves the path and decides
 * recursion from a live stat of its own. Should a directory occupy the path by the time the user
 * confirms, passing it on would walk a whole tree behind a dialog that named a single document.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PdfViewerViewModelDeleteTest {

    @get:Rule
    val rule = PdfViewerTestRule()

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("pdfviewer_delete").toFile()
        mockkObject(MediaStoreUtil)
        coEvery { MediaStoreUtil.notifyDeleted(any(), any()) } just Runs
        every { MediaStoreUtil.scanFiles(any(), any()) } just Runs
        // Returning success is what makes the refusals below earn their green: without the guard
        // the delete would go through and the event would be Finish.
        coEvery { rule.fileRepository.delete(any()) } answers {
            DeleteResult(removedPaths = firstArg<List<FileItem>>().map { it.path })
        }
    }

    @After
    fun tearDown() {
        unmockkObject(MediaStoreUtil)
        tempDir.deleteRecursively()
    }

    @Test
    fun `deleting refuses a path a directory now occupies`() = runTest(rule.dispatcher) {
        val directory = File(tempDir, "report.pdf").apply { mkdirs() }
        val child = File(directory, "inside.pdf").apply { writeText("keep me") }
        val viewModel = rule.viewModel(FakePdfDocumentOpener(), filePath = directory.absolutePath)
        rule.advance()

        viewModel.events.test {
            viewModel.onDeleteConfirmed()
            rule.advance()

            assertEquals(PdfViewerUiEvent.ShowToast(R.string.delete_error), awaitItem())
        }
        coVerify(exactly = 0) { rule.fileRepository.delete(any()) }
        assertTrue("The ViewModel must not delete outside the repository", child.exists())
    }

    @Test
    fun `deleting refuses a directory that replaced the file after the screen opened`() =
        runTest(rule.dispatcher) {
            val file = File(tempDir, "report.pdf").apply { writeText("%PDF-") }
            val viewModel = rule.viewModel(FakePdfDocumentOpener(), filePath = file.absolutePath)
            rule.advance()

            // What an external writer does to the path while the viewer sits open.
            file.delete()
            val directory = File(tempDir, "report.pdf").apply { mkdirs() }
            val child = File(directory, "inside.pdf").apply { writeText("keep me") }

            viewModel.events.test {
                viewModel.onDeleteConfirmed()
                rule.advance()

                assertEquals(PdfViewerUiEvent.ShowToast(R.string.delete_error), awaitItem())
            }
            coVerify(exactly = 0) { rule.fileRepository.delete(any()) }
            assertTrue("The ViewModel must not delete outside the repository", child.exists())
        }

    @Test
    fun `deleting still removes the single file the viewer was opened on`() = runTest(rule.dispatcher) {
        // The guard above must not cost the user the delete this screen exists to offer.
        val file = File(tempDir, "report.pdf").apply { writeText("%PDF-") }
        val viewModel = rule.viewModel(FakePdfDocumentOpener(), filePath = file.absolutePath)
        rule.advance()

        viewModel.events.test {
            viewModel.onDeleteConfirmed()
            rule.advance()

            assertEquals(PdfViewerUiEvent.Finish, awaitItem())
        }
        coVerify(exactly = 1) { rule.fileRepository.delete(match { it.single().path == file.absolutePath }) }
        coVerify(exactly = 1) { MediaStoreUtil.notifyDeleted(rule.application, listOf(file.absolutePath)) }
    }

    @Test
    fun `a failed delete stays on screen and says why`() = runTest(rule.dispatcher) {
        val file = File(tempDir, "report.pdf").apply { writeText("%PDF-") }
        coEvery { rule.fileRepository.delete(any()) } returns
            DeleteResult(failedCount = 1, failureErrno = ERRNO_UNKNOWN)
        val viewModel = rule.viewModel(FakePdfDocumentOpener(), filePath = file.absolutePath)
        rule.advance()

        viewModel.events.test {
            viewModel.onDeleteConfirmed()
            rule.advance()

            assertEquals(PdfViewerUiEvent.ShowToast(DeleteFailure.UNKNOWN.messageResId), awaitItem())
            expectNoEvents()
        }
        // The guard's refusal shows the same message, so this is what proves the delete was tried.
        coVerify(exactly = 1) { rule.fileRepository.delete(any()) }
        coVerify(exactly = 0) { MediaStoreUtil.notifyDeleted(any(), any()) }
    }
}
