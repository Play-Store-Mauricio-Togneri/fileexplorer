package com.mauriciotogneri.fileexplorer.ui.screens.textviewer

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * [TextViewerViewModel] had no test at any level.
 *
 * It runs as an instrumentation test because the view model is an `AndroidViewModel` that reaches
 * `IntentUtil.trackRecentFile` and `MediaStoreUtil` through a real `Context`; on the JVM those are
 * unimplemented stubs.
 *
 * The behaviour that matters is at the edges: a file larger than [TextViewerViewModel.MAX_BYTES]
 * must be truncated rather than loaded whole (the cap exists to keep a single selectable buffer from
 * causing jank), and an unreadable file must land in the error state rather than crashing.
 */
@RunWith(AndroidJUnit4::class)
class TextViewerViewModelTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val application = context.applicationContext as Application

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(context.cacheDir, "test_textviewer_${System.currentTimeMillis()}")
            .apply { mkdirs() }
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    private fun viewModel(file: File) = TextViewerViewModel(
        filePath = file.absolutePath,
        source = "test",
        application = application,
        fileRepository = FileRepository()
    )

    /** Waits for the initial async load to settle. */
    private fun loadedState(file: File): TextViewerUiState = runBlocking {
        val vm = viewModel(file)
        withTimeout(10_000) { vm.state.first { !it.isLoading } }
    }

    // ==================== Loading ====================

    @Test
    fun load_readsEveryLine() {
        val file = File(testDir, "notes.txt").apply {
            writeText("first\nsecond\nthird")
        }

        val state = loadedState(file)

        assertFalse("Should not report an error for a readable file", state.error)
        assertEquals(listOf("first", "second", "third"), state.lines)
        assertFalse("A small file is not truncated", state.truncated)
        assertEquals("notes.txt", state.fileName)
        assertNotNull("The FileItem backs share/delete, so it must be populated", state.file)
    }

    @Test
    fun load_emptyFile_succeedsWithNoLines() {
        val file = File(testDir, "empty.txt").apply { createNewFile() }

        val state = loadedState(file)

        assertFalse("An empty file is readable, not an error", state.error)
        assertTrue(state.lines.isEmpty())
        assertFalse(state.truncated)
    }

    /** The file name is shown in the toolbar before the content arrives, so it must be set eagerly. */
    @Test
    fun fileName_isAvailableBeforeLoadingCompletes() {
        val file = File(testDir, "eager_name.txt").apply { writeText("x") }

        val vm = viewModel(file)

        assertEquals("eager_name.txt", vm.state.value.fileName)
    }

    // ==================== The size cap ====================

    @Test
    fun load_fileOverTheCap_isTruncated() {
        val file = File(testDir, "huge.txt").apply {
            // Comfortably past MAX_BYTES, in lines short enough that many are dropped.
            writeText(("abcdefghij\n").repeat(TextViewerViewModel.MAX_BYTES / 5))
        }

        val state = loadedState(file)

        assertFalse("A large file still loads", state.error)
        assertTrue("A file past the cap must be flagged as truncated", state.truncated)
        assertTrue("Some content should still be shown", state.lines.isNotEmpty())
    }

    @Test
    fun load_fileJustUnderTheCap_isNotTruncated() {
        val file = File(testDir, "just_under.txt").apply {
            writeText("a".repeat(TextViewerViewModel.MAX_BYTES - 1024))
        }

        val state = loadedState(file)

        assertFalse("Under the cap nothing is dropped", state.truncated)
    }

    // ==================== Failure ====================

    @Test
    fun load_missingFile_entersErrorState() {
        val missing = File(testDir, "does_not_exist.txt")

        val state = loadedState(missing)

        assertTrue("A missing file should surface as the error state", state.error)
        assertFalse("Loading must finish even on failure", state.isLoading)
    }

    @Test
    fun load_directory_entersErrorState() {
        val directory = File(testDir, "a_folder").apply { mkdirs() }

        val state = loadedState(directory)

        assertTrue("A directory is not readable as text", state.error)
    }

    // ==================== Delete ====================

    /**
     * `events` is a hot [kotlinx.coroutines.flow.SharedFlow], so the collector has to be running
     * before the action is triggered or the emission is missed.
     */
    private fun awaitEventAfter(vm: TextViewerViewModel, action: () -> Unit): TextViewerUiEvent =
        runBlocking {
            val subscribed = CompletableDeferred<Unit>()
            val collected = CompletableDeferred<TextViewerUiEvent>()
            val collector = launch {
                vm.events
                    .onSubscription { subscribed.complete(Unit) }
                    .collect { collected.complete(it) }
            }
            // Only act once the collector is registered, or the emission is dropped.
            subscribed.await()
            action()
            val event = withTimeout(10_000) { collected.await() }
            collector.cancel()
            event
        }

    @Test
    fun onDeleteConfirmed_removesTheFileAndFinishes() {
        val file = File(testDir, "delete_me.txt").apply { writeText("bye") }
        val vm = viewModel(file)
        runBlocking { withTimeout(10_000) { vm.state.first { !it.isLoading } } }

        val event = awaitEventAfter(vm) { vm.onDeleteConfirmed() }

        assertEquals(TextViewerUiEvent.Finish, event)
        assertFalse("The file should be gone", file.exists())
    }

    /**
     * A path that already holds nothing satisfies the delete the user asked for: `removePath`
     * answers AlreadyAbsent for ENOENT, so the screen closes instead of reporting an error for a
     * file something else removed first. `FolderErrorStatesTest.delete_nonExistentFile_countsAsDone`
     * pins the same contract one layer down.
     */
    @Test
    fun onDeleteConfirmed_whenTheFileIsAlreadyGone_finishes() {
        val missing = File(testDir, "already_gone.txt")
        val vm = viewModel(missing)
        runBlocking { withTimeout(10_000) { vm.state.first { !it.isLoading } } }

        val event = awaitEventAfter(vm) { vm.onDeleteConfirmed() }

        assertEquals(TextViewerUiEvent.Finish, event)
    }

    /**
     * A delete that fails must tell the user rather than closing the screen as if it had worked.
     *
     * The failure is staged on the filesystem rather than through a stub repository: the file sits
     * in a read-only directory, so the real `Os.remove` answers EACCES and the path the screen
     * branches on is the production one.
     */
    @Test
    fun onDeleteConfirmed_whenDeleteFails_showsAToastInsteadOfFinishing() {
        val blocked = File(testDir, "blocked").apply { mkdirs() }
        val file = File(blocked, "undeletable.txt").apply { writeText("stays") }
        blocked.setWritable(false, false)
        // Root unlinks from a read-only directory regardless, so the failure cannot be staged there.
        assumeTrue(!blocked.canWrite())
        val vm = viewModel(file)
        runBlocking { withTimeout(10_000) { vm.state.first { !it.isLoading } } }

        val event = awaitEventAfter(vm) { vm.onDeleteConfirmed() }

        // Restored before the assertions so that a failure still leaves tearDown able to clean up.
        blocked.setWritable(true, true)

        // The errno's own message, not the generic one: this screen also emits
        // ShowToast(delete_error) from the directory guard, so only the specific resource pins that
        // the failure came back from the delete rather than from the guard in front of it.
        assertEquals(
            "A failed delete should report an error, not finish",
            TextViewerUiEvent.ShowToast(R.string.delete_error_permission),
            event
        )
        assertTrue("The file should survive a failed delete", file.exists())
    }
}
