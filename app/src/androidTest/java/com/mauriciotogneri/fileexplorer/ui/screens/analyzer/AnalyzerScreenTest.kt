package com.mauriciotogneri.fileexplorer.ui.screens.analyzer

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.SearchFileType
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.AnalyzerRepository
import com.mauriciotogneri.fileexplorer.data.repository.ScanProgress
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.source.StorageSource
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The analyzer's three steps and the prompt that guards a running scan, driven through the real
 * [AnalyzerViewModel] against a fake volume list and a fake walk. The walk is a hot flow the test
 * pushes into, so a scan can be held open at a chosen point rather than racing to completion.
 */
@RunWith(AndroidJUnit4::class)
class AnalyzerScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private val progress = MutableSharedFlow<ScanProgress>(extraBufferCapacity = 16)

    private val internal = StorageDevice(
        path = "/storage/emulated/0",
        displayName = "Internal storage",
        totalBytes = 1_000L,
        availableBytes = 400L
    )
    private val sdCard = StorageDevice(
        path = "/storage/1234-5678",
        displayName = "SD card",
        totalBytes = 2_000L,
        availableBytes = 1_500L
    )

    @Test
    fun selection_withOneVolume_preselectsItAndEnablesAnalyze() {
        renderAnalyzer(listOf(internal))

        composeTestRule.onNodeWithText(internal.displayName).assertIsSelected()
        composeTestRule.onNodeWithText(string(R.string.analyzer_analyze)).assertIsEnabled()
    }

    @Test
    fun selection_withTwoVolumes_leavesAnalyzeDisabledUntilOneIsChosen() {
        renderAnalyzer(listOf(internal, sdCard))

        composeTestRule.onNodeWithText(string(R.string.analyzer_analyze)).assertIsNotEnabled()

        composeTestRule.onNodeWithText(sdCard.displayName).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(sdCard.displayName).assertIsSelected()
        composeTestRule.onNodeWithText(string(R.string.analyzer_analyze)).assertIsEnabled()
    }

    @Test
    fun scanning_showsTheFolderBeingWalked() {
        renderAnalyzer(listOf(internal))
        startScan()

        emit(scanProgress(scannedBytes = 300L, currentFolder = "/storage/emulated/0/DCIM"))

        composeTestRule.onNodeWithText(string(R.string.analyzer_scanning)).assertIsDisplayed()
        composeTestRule.onNodeWithText("/storage/emulated/0/DCIM").assertIsDisplayed()
    }

    @Test
    fun scanning_cancelButton_raisesThePromptWithoutLeavingTheScan() {
        renderAnalyzer(listOf(internal))
        startScan()

        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.analyzer_stop_scanning_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.analyzer_scanning)).assertIsDisplayed()
    }

    @Test
    fun scanning_backPress_raisesTheSamePromptRatherThanCancelling() {
        renderAnalyzer(listOf(internal))
        startScan()

        pressBack()

        composeTestRule.onNodeWithText(string(R.string.analyzer_stop_scanning_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.analyzer_scanning)).assertIsDisplayed()
    }

    @Test
    fun scanning_continue_dismissesThePromptAndKeepsScanning() {
        renderAnalyzer(listOf(internal))
        startScan()

        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.analyzer_stop_scanning_dismiss)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.analyzer_scanning)).assertIsDisplayed()

        emit(scanProgress(scannedBytes = 500L, currentFolder = "/storage/emulated/0/Movies"))

        composeTestRule.onNodeWithText("/storage/emulated/0/Movies").assertIsDisplayed()
    }

    @Test
    fun scanning_stop_returnsToTheVolumeList() {
        renderAnalyzer(listOf(internal))
        startScan()

        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.analyzer_stop_scanning_confirm)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.analyzer_analyze)).assertIsDisplayed()
        composeTestRule.onNodeWithText(internal.displayName).assertIsSelected()
    }

    @Test
    fun results_listEveryCategoryIncludingSystem() {
        renderAnalyzer(listOf(internal))
        startScan()

        emit(
            scanProgress(
                scannedBytes = 400L,
                isComplete = true,
                sizes = mapOf(
                    SearchFileType.IMAGES to 100L,
                    SearchFileType.VIDEOS to 210L,
                    SearchFileType.AUDIO to 50L,
                    SearchFileType.DOCUMENTS to 30L,
                    SearchFileType.OTHER to 10L
                )
            )
        )

        composeTestRule.onNodeWithText(string(R.string.location_images)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.location_videos)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.location_audio)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.location_documents)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.search_filter_type_other)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.analyzer_category_system)).assertIsDisplayed()

        // 600 used, 400 accounted for by files: the rest is the system slice. Every category in
        // the fixture is given a distinct size, so this matches the system row and only that row.
        composeTestRule.onNodeWithText(FileSizeFormatter.format(200L)).assertIsDisplayed()
    }

    /**
     * The centre carries the same fact in two units, so each figure needs the word that tells them
     * apart — without them "60%" and "600 B" read as two unrelated measurements.
     */
    @Test
    fun results_centreLabelsBothFigures() {
        renderAnalyzer(listOf(internal))
        startScan()
        // Sized so that no category row repeats a figure the centre shows: without them every
        // category is zero and the system slice takes the whole 600 B the centre is reporting.
        emit(
            scanProgress(
                scannedBytes = 400L,
                isComplete = true,
                sizes = mapOf(
                    SearchFileType.IMAGES to 100L,
                    SearchFileType.VIDEOS to 210L,
                    SearchFileType.AUDIO to 50L,
                    SearchFileType.DOCUMENTS to 30L,
                    SearchFileType.OTHER to 10L
                )
            )
        )

        // 600 of 1,000 bytes in use.
        composeTestRule.onNodeWithText("60%", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.analyzer_used), useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(FileSizeFormatter.format(600L), useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            activity.getString(R.string.analyzer_of_total, FileSizeFormatter.format(1_000L)),
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    @Test
    fun results_backPress_returnsToTheVolumeList() {
        renderAnalyzer(listOf(internal))
        startScan()
        emit(scanProgress(scannedBytes = 400L, isComplete = true))

        pressBack()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.analyzer_analyze)).assertIsDisplayed()
    }

    private fun renderAnalyzer(storages: List<StorageDevice>) {
        val viewModel = AnalyzerViewModel(
            storageRepository = StorageRepository(FakeStorageSource(storages)),
            analyzerRepository = FakeAnalyzerRepository(progress)
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                AnalyzerScreen(viewModel = viewModel, onCloseClick = {})
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun startScan() {
        composeTestRule.onNodeWithText(string(R.string.analyzer_analyze)).performClick()
        composeTestRule.waitForIdle()
    }

    /**
     * Emitted from the test thread and then waited on, so the assertion that follows runs against a
     * frame that already carries this snapshot.
     */
    private fun emit(snapshot: ScanProgress) {
        runBlocking { progress.emit(snapshot) }
        composeTestRule.waitForIdle()
    }

    private fun pressBack() {
        composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    private fun scanProgress(
        scannedBytes: Long,
        currentFolder: String = internal.path,
        isComplete: Boolean = false,
        sizes: Map<SearchFileType, Long> = emptyMap()
    ) = ScanProgress(
        currentFolder = currentFolder,
        scannedBytes = scannedBytes,
        fileCount = 0,
        sizesByType = SearchFileType.entries.associateWith { sizes[it] ?: 0L },
        isComplete = isComplete
    )

    private fun string(@StringRes id: Int): String = activity.getString(id)

    private class FakeStorageSource(private val storages: List<StorageDevice>) : StorageSource {
        override suspend fun getStorages(): List<StorageDevice> = storages
    }

    private class FakeAnalyzerRepository(
        private val progress: Flow<ScanProgress>
    ) : AnalyzerRepository() {
        override fun analyze(rootPath: String): Flow<ScanProgress> = progress
    }
}
