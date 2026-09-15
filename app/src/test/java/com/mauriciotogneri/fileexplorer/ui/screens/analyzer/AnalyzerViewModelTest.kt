package com.mauriciotogneri.fileexplorer.ui.screens.analyzer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerCategory
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerFileEntry
import com.mauriciotogneri.fileexplorer.data.model.SearchFileType
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.model.StorageType
import com.mauriciotogneri.fileexplorer.data.repository.AnalyzerRepository
import com.mauriciotogneri.fileexplorer.data.repository.AnalyzerResultsHolder
import com.mauriciotogneri.fileexplorer.data.repository.ScanProgress
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageUnavailableException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyzerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storageRepository: StorageRepository
    private lateinit var analyzerRepository: AnalyzerRepository
    private val createdViewModels = mutableListOf<AnalyzerViewModel>()

    private val internal = StorageDevice(
        path = "/storage/emulated/0",
        displayName = "Internal storage",
        totalBytes = 1_000L,
        availableBytes = 400L,
        type = StorageType.INTERNAL
    )
    private val photo = AnalyzerFileEntry(path = "/storage/emulated/0/DCIM/photo.jpg", size = 400L)
    private val sdCard = StorageDevice(
        path = "/storage/1234-5678",
        displayName = "SD card",
        totalBytes = 2_000L,
        availableBytes = 1_500L,
        type = StorageType.SD_CARD
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        storageRepository = mockk(relaxed = true)
        analyzerRepository = mockk(relaxed = true)
        coEvery { storageRepository.getStorages() } returns emptyList()
        AnalyzerResultsHolder.clear()
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
        AnalyzerResultsHolder.clear()
    }

    @Test
    fun `a completed scan hands its file lists to the listing`() = runTest {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val viewModel = scanningViewModel(progress)

        progress.send(
            scanProgress(
                scannedBytes = 500L,
                isComplete = true,
                sizes = mapOf(SearchFileType.IMAGES to 500L),
                largest = mapOf(SearchFileType.IMAGES to listOf(photo))
            )
        )
        advanceUntilIdle()

        val images = AnalyzerResultsHolder.filesFor(AnalyzerCategory.IMAGES)
        assertEquals(listOf(photo), images?.entries)
        // The figure the listing states is the one the chart row shows, not the sum of its rows.
        assertEquals(500L, images?.totalBytes)
    }

    @Test
    fun `a category the scan found nothing for is handed over empty rather than missing`() = runTest {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val viewModel = scanningViewModel(progress)

        progress.send(scanProgress(scannedBytes = 0L, isComplete = true))
        advanceUntilIdle()

        val videos = AnalyzerResultsHolder.filesFor(AnalyzerCategory.VIDEOS)
        assertEquals(emptyList<AnalyzerFileEntry>(), videos?.entries)
    }

    @Test
    fun `the unaccounted remainder is not handed over at all`() = runTest {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val viewModel = scanningViewModel(progress)

        progress.send(scanProgress(scannedBytes = 0L, isComplete = true))
        advanceUntilIdle()

        assertNull(AnalyzerResultsHolder.filesFor(AnalyzerCategory.SYSTEM))
    }

    @Test
    fun `a new scan drops the previous scan's file lists`() = runTest {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val viewModel = scanningViewModel(progress)
        progress.send(
            scanProgress(
                scannedBytes = 500L,
                isComplete = true,
                largest = mapOf(SearchFileType.IMAGES to listOf(photo))
            )
        )
        advanceUntilIdle()

        viewModel.startScan()
        advanceUntilIdle()

        assertNull(AnalyzerResultsHolder.filesFor(AnalyzerCategory.IMAGES))
    }

    @Test
    fun `leaving the results drops the file lists`() = runTest {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val viewModel = scanningViewModel(progress)
        progress.send(
            scanProgress(
                scannedBytes = 500L,
                isComplete = true,
                largest = mapOf(SearchFileType.IMAGES to listOf(photo))
            )
        )
        advanceUntilIdle()

        viewModel.backToSelection()

        assertNull(AnalyzerResultsHolder.filesFor(AnalyzerCategory.IMAGES))
    }

    @Test
    fun `cancelling drops file lists the walk handed over behind the prompt`() = runTest {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val viewModel = scanningViewModel(progress)
        viewModel.requestCancelScan()

        // The race the prompt lives with, as the flag's own comment describes it: the walk keeps
        // running behind the dialog, so it can finish and hand its lists over while a tap on
        // "stop" is already on its way.
        progress.send(
            scanProgress(
                scannedBytes = 500L,
                isComplete = true,
                largest = mapOf(SearchFileType.IMAGES to listOf(photo))
            )
        )
        advanceUntilIdle()
        assertNotNull(AnalyzerResultsHolder.filesFor(AnalyzerCategory.IMAGES))

        viewModel.confirmCancelScan()

        assertNull(AnalyzerResultsHolder.filesFor(AnalyzerCategory.IMAGES))
    }

    @Test
    fun `selects the only volume automatically`() = runTest {
        coEvery { storageRepository.getStorages() } returns listOf(internal)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(internal.path, viewModel.uiState.value.selectedPath)
        assertEquals(AnalyzerStep.SELECTION, viewModel.uiState.value.step)
    }

    @Test
    fun `selects nothing when there is more than one volume`() = runTest {
        coEvery { storageRepository.getStorages() } returns listOf(internal, sdCard)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedPath)
    }

    @Test
    fun `selectStorage picks the volume the user tapped`() = runTest {
        coEvery { storageRepository.getStorages() } returns listOf(internal, sdCard)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectStorage(sdCard.path)

        assertEquals(sdCard, viewModel.uiState.value.selectedStorage)
    }

    @Test
    fun `startScan captures the used space of the selected volume`() = runTest {
        val viewModel = scanningViewModel(Channel())

        assertEquals(AnalyzerStep.SCANNING, viewModel.uiState.value.step)
        assertEquals(600L, viewModel.uiState.value.usedBytes)
    }

    @Test
    fun `the running total the scanning screen shows follows the walk`() = runTest {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val viewModel = scanningViewModel(progress)

        progress.send(
            scanProgress(
                scannedBytes = 300L,
                fileCount = 12,
                currentFolder = "/storage/emulated/0/DCIM"
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(300L, state.scannedBytes)
        assertEquals(12, state.fileCount)
        assertEquals("/storage/emulated/0/DCIM", state.currentFolder)
        assertEquals(AnalyzerStep.SCANNING, state.step)
    }

    @Test
    fun `starting a scan clears the running total of the previous one`() = runTest {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val viewModel = scanningViewModel(progress)

        progress.send(scanProgress(scannedBytes = 300L, fileCount = 12))
        advanceUntilIdle()

        viewModel.requestCancelScan()
        viewModel.confirmCancelScan()
        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()

        assertEquals(0L, viewModel.uiState.value.scannedBytes)
        assertEquals(0, viewModel.uiState.value.fileCount)
    }

    @Test
    fun `a completed scan breaks the used space into six categories`() = runTest {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val viewModel = scanningViewModel(progress)

        progress.send(
            scanProgress(
                scannedBytes = 400L,
                isComplete = true,
                sizes = mapOf(
                    SearchFileType.IMAGES to 100L,
                    SearchFileType.VIDEOS to 200L,
                    SearchFileType.AUDIO to 50L,
                    SearchFileType.DOCUMENTS to 30L,
                    SearchFileType.OTHER to 20L
                )
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AnalyzerStep.RESULTS, state.step)
        assertEquals(AnalyzerCategory.entries, state.categories.map { it.category })

        // 600 used, 400 seen: the 200 nobody could account for is the system slice.
        assertEquals(200L, state.categories.single { it.category == AnalyzerCategory.SYSTEM }.bytes)
        assertEquals(600L, state.categories.sumOf { it.bytes })
        assertEquals(1f, state.categories.map { it.fraction }.sum(), 0.001f)
    }

    @Test
    fun `a file the listing deleted comes off its slice and off the used total`() = runTest {
        val viewModel = chartedViewModel()

        AnalyzerResultsHolder.remove(AnalyzerCategory.IMAGES, setOf(photo.path))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0L, state.categories.single { it.category == AnalyzerCategory.IMAGES }.bytes)
        assertEquals(100L, state.categories.single { it.category == AnalyzerCategory.VIDEOS }.bytes)
        // 600 used, 400 of it the photo that just went.
        assertEquals(200L, state.usedBytes)
        assertEquals(0.2f, state.usedFraction, 0.001f)
        assertEquals(1f, state.categories.map { it.fraction }.sum(), 0.001f)
    }

    @Test
    fun `the unaccounted remainder does not absorb what the listing deleted`() = runTest {
        val viewModel = chartedViewModel()

        AnalyzerResultsHolder.remove(AnalyzerCategory.IMAGES, setOf(photo.path))
        advanceUntilIdle()

        // The freed bytes left the volume; they did not become space the walk could not see.
        val system = viewModel.uiState.value.categories.single { it.category == AnalyzerCategory.SYSTEM }
        assertEquals(100L, system.bytes)
    }

    @Test
    fun `the volume figures are re-read once the listing has freed space`() = runTest {
        val viewModel = chartedViewModel()
        val afterDelete = internal.copy(availableBytes = internal.availableBytes + 400L)
        coEvery { storageRepository.getStorages() } returns listOf(afterDelete)

        AnalyzerResultsHolder.remove(AnalyzerCategory.IMAGES, setOf(photo.path))
        advanceUntilIdle()

        // Left at the figures captured when this screen opened, the volume list would still report
        // the deleted space as in use, and a second scan would start from that total and hand every
        // freed byte to the system slice.
        assertEquals(listOf(afterDelete), viewModel.uiState.value.storages)
    }

    @Test
    fun `a removal that matches nothing leaves the chart alone`() = runTest {
        val viewModel = chartedViewModel()
        val before = viewModel.uiState.value

        AnalyzerResultsHolder.remove(AnalyzerCategory.IMAGES, setOf("/storage/emulated/0/gone.jpg"))
        advanceUntilIdle()

        assertEquals(before.categories, viewModel.uiState.value.categories)
        assertEquals(before.usedBytes, viewModel.uiState.value.usedBytes)
    }

    @Test
    fun `the system slice is never negative when the scan overshoots`() = runTest {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val viewModel = scanningViewModel(progress)

        progress.send(
            scanProgress(
                scannedBytes = 900L,
                isComplete = true,
                sizes = mapOf(SearchFileType.IMAGES to 900L)
            )
        )
        advanceUntilIdle()

        val system = viewModel.uiState.value.categories.single { it.category == AnalyzerCategory.SYSTEM }
        assertEquals(0L, system.bytes)
        assertEquals(0f, system.fraction, 0.001f)
    }

    @Test
    fun `requestCancelScan raises the prompt without stopping the walk`() = runTest {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val viewModel = scanningViewModel(progress)

        viewModel.requestCancelScan()

        assertTrue(viewModel.uiState.value.showCancelConfirmation)
        assertEquals(AnalyzerStep.SCANNING, viewModel.uiState.value.step)

        // Still collecting: the walk was never touched, so it has nothing to resume from.
        progress.send(scanProgress(scannedBytes = 120L))
        advanceUntilIdle()

        assertEquals(120L, viewModel.uiState.value.scannedBytes)
    }

    @Test
    fun `dismissCancelScan lowers the prompt and leaves the scan running`() = runTest {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val viewModel = scanningViewModel(progress)

        viewModel.requestCancelScan()
        viewModel.dismissCancelScan()

        assertFalse(viewModel.uiState.value.showCancelConfirmation)
        assertEquals(AnalyzerStep.SCANNING, viewModel.uiState.value.step)

        progress.send(scanProgress(scannedBytes = 240L))
        advanceUntilIdle()

        assertEquals(240L, viewModel.uiState.value.scannedBytes)
    }

    @Test
    fun `confirmCancelScan returns to the volume list and discards the partial tally`() = runTest {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val viewModel = scanningViewModel(progress)

        progress.send(scanProgress(scannedBytes = 300L))
        advanceUntilIdle()

        viewModel.requestCancelScan()
        viewModel.confirmCancelScan()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AnalyzerStep.SELECTION, state.step)
        assertFalse(state.showCancelConfirmation)
        assertEquals(0L, state.scannedBytes)
        assertTrue(state.categories.isEmpty())
        // The volume stays chosen, so re-running is one tap.
        assertEquals(internal.path, state.selectedPath)
    }

    @Test
    fun `a cancelled scan stops updating the screen`() = runTest {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val viewModel = scanningViewModel(progress)

        viewModel.requestCancelScan()
        viewModel.confirmCancelScan()
        advanceUntilIdle()

        // trySend rather than send: cancelling the collector closes the channel behind
        // consumeAsFlow, so send would throw the cancellation rather than report it. The failed
        // result is the assertion — nothing is listening to the walk any more.
        assertTrue(progress.trySend(scanProgress(scannedBytes = 500L, isComplete = true)).isFailure)
        advanceUntilIdle()

        assertEquals(AnalyzerStep.SELECTION, viewModel.uiState.value.step)
        assertEquals(0L, viewModel.uiState.value.scannedBytes)
    }

    @Test
    fun `requestCancelScan does nothing outside a scan`() = runTest {
        coEvery { storageRepository.getStorages() } returns listOf(internal)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestCancelScan()

        assertFalse(viewModel.uiState.value.showCancelConfirmation)
    }

    @Test
    fun `backToSelection clears the results and keeps the volume`() = runTest {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val viewModel = scanningViewModel(progress)

        progress.send(scanProgress(scannedBytes = 400L, isComplete = true))
        advanceUntilIdle()

        viewModel.backToSelection()

        val state = viewModel.uiState.value
        assertEquals(AnalyzerStep.SELECTION, state.step)
        assertTrue(state.categories.isEmpty())
        assertEquals(internal.path, state.selectedPath)
    }

    @Test
    fun `a volume that leaves mid-scan returns to the list instead of charting a partial tally`() = runTest {
        coEvery { storageRepository.getStorages() } returns listOf(internal)
        every { analyzerRepository.analyze(internal.path) } returns
            flow { throw StorageUnavailableException() }

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AnalyzerStep.SELECTION, state.step)
        assertTrue(state.categories.isEmpty())
        assertEquals(0L, state.scannedBytes)
        assertEquals(R.string.analyzer_error_storage_unavailable, state.errorResId)
    }

    @Test
    fun `a volume that leaves mid-scan is taken off the list the user lands back on`() = runTest {
        coEvery { storageRepository.getStorages() } returns listOf(internal, sdCard)
        every { analyzerRepository.analyze(sdCard.path) } returns
            flow { throw StorageUnavailableException() }

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectStorage(sdCard.path)
        // The card is gone by the time the walk gives up on it, which is why the walk gave up.
        coEvery { storageRepository.getStorages() } returns listOf(internal)

        viewModel.startScan()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Left as it was, the list would go on offering the card, under the capacity it reported
        // while it was still there, and every retry would fail the same way with nothing on screen
        // to say why.
        assertEquals(listOf(internal), state.storages)
        // And the selection moves off it: a path no card resolves to leaves the Analyze button
        // offering to start a scan that startScan declines without a word. One volume is left, so
        // the rule the screen opens on applies again and it is chosen.
        assertEquals(internal.path, state.selectedPath)
    }

    @Test
    fun `a selection nothing on the refreshed list resolves to is dropped`() = runTest {
        val viewModel = chartedViewModel()
        // Both volumes gone, so there is nothing left to fall back to either.
        coEvery { storageRepository.getStorages() } returns emptyList()

        AnalyzerResultsHolder.remove(AnalyzerCategory.IMAGES, setOf(photo.path))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedPath)
    }

    @Test
    fun `clearing the view model releases the scan's file lists`() = runTest {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val store = ViewModelStore()
        val viewModel = scanningViewModel(progress, store)

        progress.send(
            scanProgress(
                scannedBytes = 500L,
                isComplete = true,
                sizes = mapOf(SearchFileType.IMAGES to 500L),
                largest = mapOf(SearchFileType.IMAGES to listOf(photo))
            )
        )
        advanceUntilIdle()
        assertNotNull(AnalyzerResultsHolder.filesFor(AnalyzerCategory.IMAGES))

        // What the framework does when the analyzer is finished with, or destroyed to reclaim
        // memory. A rotation does not reach here, which is what lets the category listing on top of
        // a rotating analyzer keep reading the lists.
        store.clear()

        assertNull(AnalyzerResultsHolder.filesFor(AnalyzerCategory.IMAGES))
        assertNull(AnalyzerResultsHolder.categories.value)
    }

    @Test
    fun `errorShown clears the one-shot message`() = runTest {
        coEvery { storageRepository.getStorages() } returns listOf(internal)
        every { analyzerRepository.analyze(internal.path) } returns
            flow { throw StorageUnavailableException() }

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()

        viewModel.errorShown()

        assertNull(viewModel.uiState.value.errorResId)
    }

    @Test
    fun `a scan that completes normally reports no error`() = runTest {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val viewModel = scanningViewModel(progress)

        progress.send(scanProgress(scannedBytes = 400L, isComplete = true))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorResId)
    }

    @Test
    fun `usedFraction is the share of the volume that is in use`() = runTest {
        val viewModel = scanningViewModel(Channel())

        assertEquals(0.6f, viewModel.uiState.value.usedFraction, 0.001f)
    }

    /**
     * A view model showing the results of a finished scan: 600 bytes used, 500 of them seen —
     * [photo]'s 400 under images and 100 under videos — leaving 100 the walk could not account for.
     */
    private fun chartedViewModel(): AnalyzerViewModel {
        val progress = Channel<ScanProgress>(Channel.UNLIMITED)
        val viewModel = scanningViewModel(progress)

        progress.trySend(
            scanProgress(
                scannedBytes = 500L,
                isComplete = true,
                sizes = mapOf(SearchFileType.IMAGES to 400L, SearchFileType.VIDEOS to 100L),
                largest = mapOf(SearchFileType.IMAGES to listOf(photo))
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        return viewModel
    }

    /**
     * A view model with [internal] selected and a scan running against [progress].
     *
     * [store] puts it in a `ViewModelStore`, so that a test can have it cleared the way the
     * framework clears it rather than by calling `onCleared` itself.
     */
    private fun scanningViewModel(
        progress: Channel<ScanProgress>,
        store: ViewModelStore? = null
    ): AnalyzerViewModel {
        coEvery { storageRepository.getStorages() } returns listOf(internal)
        every { analyzerRepository.analyze(internal.path) } returns progress.consumeAsFlow()

        val viewModel = store?.let {
            ViewModelProvider(it, viewModelFactory)[AnalyzerViewModel::class.java]
                .also { created -> createdViewModels.add(created) }
        } ?: createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.startScan()
        testDispatcher.scheduler.advanceUntilIdle()

        return viewModel
    }

    /** Builds the same view model [createViewModel] does, for a store to own. */
    private val viewModelFactory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AnalyzerViewModel(
            storageRepository = storageRepository,
            analyzerRepository = analyzerRepository,
            ioDispatcher = testDispatcher
        ) as T
    }

    private fun scanProgress(
        scannedBytes: Long,
        currentFolder: String = internal.path,
        isComplete: Boolean = false,
        fileCount: Int = 0,
        sizes: Map<SearchFileType, Long> = emptyMap(),
        largest: Map<SearchFileType, List<AnalyzerFileEntry>> = emptyMap()
    ) = ScanProgress(
        currentFolder = currentFolder,
        scannedBytes = scannedBytes,
        fileCount = fileCount,
        sizesByType = SearchFileType.entries.associateWith { sizes[it] ?: 0L },
        largestByType = largest,
        isComplete = isComplete
    )

    private fun createViewModel(): AnalyzerViewModel = AnalyzerViewModel(
        storageRepository = storageRepository,
        analyzerRepository = analyzerRepository,
        ioDispatcher = testDispatcher
    ).also { createdViewModels.add(it) }
}
