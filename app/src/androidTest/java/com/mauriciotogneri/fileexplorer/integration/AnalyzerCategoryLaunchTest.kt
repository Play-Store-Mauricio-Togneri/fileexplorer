package com.mauriciotogneri.fileexplorer.integration

import android.app.Activity
import android.app.Instrumentation
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.AnalyzerCategoryActivity
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerCategory
import com.mauriciotogneri.fileexplorer.data.model.SearchFileType
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.model.StorageType
import com.mauriciotogneri.fileexplorer.data.repository.AnalyzerRepository
import com.mauriciotogneri.fileexplorer.data.repository.AnalyzerResultsHolder
import com.mauriciotogneri.fileexplorer.data.repository.ScanProgress
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.source.StorageSource
import com.mauriciotogneri.fileexplorer.ui.screens.analyzer.AnalyzerScreen
import com.mauriciotogneri.fileexplorer.ui.screens.analyzer.AnalyzerViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.any
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Analyzer results -> category listing. The five file categories open the listing; the system row,
 * which stands for the volume's unaccounted remainder rather than a set of files, opens nothing.
 *
 * Asserts the component and the category the intent carries, matched by value because the extra's
 * key is private to the activity. The target never really starts here:
 * `AnalyzerCategoryActivityLaunchTest` covers the activity reading that extra back.
 */
@RunWith(AndroidJUnit4::class)
class AnalyzerCategoryLaunchTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private val progress = MutableSharedFlow<ScanProgress>(extraBufferCapacity = 16)

    private val internal = StorageDevice(
        path = "/storage/emulated/0",
        displayName = "Internal storage",
        totalBytes = 1_000L,
        availableBytes = 400L,
        type = StorageType.INTERNAL
    )

    @Before
    fun setUp() {
        Intents.init()
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        // The scan this test completes hands its file lists to a process-level holder.
        AnalyzerResultsHolder.clear()
    }

    // The component alone passes a launch that drops the category, which opens a listing that
    // closes itself, or one that names the wrong category: each row must send its own.
    @Test
    fun everyFileCategoryRowTap_launchesTheListingForThatCategory() {
        renderResults()

        (AnalyzerCategory.entries - AnalyzerCategory.SYSTEM).forEach { category ->
            val label = string(category.labelResId)
            composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText(label))
            composeTestRule.onNodeWithText(label).performClick()
            composeTestRule.waitForIdle()

            intended(
                allOf(
                    hasComponent(AnalyzerCategoryActivity::class.java.name),
                    hasExtra(any(String::class.java), equalTo(category.name))
                )
            )
        }
    }

    @Test
    fun systemRowTap_launchesNothing() {
        renderResults()

        composeTestRule.onNodeWithText(string(R.string.analyzer_category_system))
            .assertHasClickAction()
            .performClick()
        composeTestRule.waitForIdle()

        Intents.assertNoUnverifiedIntents()
    }

    private fun renderResults() {
        val viewModel = AnalyzerViewModel(
            storageRepository = StorageRepository(FakeStorageSource(listOf(internal))),
            analyzerRepository = FakeAnalyzerRepository(progress)
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                AnalyzerScreen(viewModel = viewModel, onCloseClick = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.analyzer_analyze)).performClick()
        composeTestRule.waitForIdle()

        runBlocking {
            progress.emit(
                ScanProgress(
                    currentFolder = internal.path,
                    scannedBytes = 400L,
                    fileCount = 4,
                    sizesByType = SearchFileType.entries.associateWith { 100L },
                    isComplete = true
                )
            )
        }
        composeTestRule.waitForIdle()
    }

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
