package com.mauriciotogneri.fileexplorer.ui.screens.search

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.ui.components.FileListItem
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchBehaviorTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun createTestFile(
        name: String,
        path: String = "/storage/emulated/0/$name",
        isDirectory: Boolean = false
    ) = FileItem(
        path = path,
        name = name,
        isDirectory = isDirectory,
        size = 1024L,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis(),
        mimeType = if (isDirectory) "" else "text/plain",
        childCount = if (isDirectory) 5 else null
    )

    // ==================== Empty State Tests ====================

    @Test
    fun search_emptyQuery_showsNothing() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchResultsContent(
                    query = "",
                    results = emptyList(),
                    isSearching = false,
                    searchComplete = false
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.search_no_results))
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag("search_central_progress").assertDoesNotExist()
    }

    @Test
    fun search_noResults_showsNoResultsMessage() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchResultsContent(
                    query = "nonexistentfile",
                    results = emptyList(),
                    isSearching = false,
                    searchComplete = true
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.search_no_results))
            .assertIsDisplayed()
    }

    @Test
    fun search_withQueryNotComplete_hidesNoResultsMessage() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchResultsContent(
                    query = "searching",
                    results = emptyList(),
                    isSearching = true,
                    searchComplete = false
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.search_no_results))
            .assertDoesNotExist()
    }

    // ==================== Progressive Results Tests ====================

    @Test
    fun search_progressiveResults_showsSpinnerAtBottom() {
        val partialResults = listOf(
            createTestFile("result1.txt"),
            createTestFile("result2.txt", path = "/storage/emulated/0/result2.txt")
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchResultsContent(
                    query = "result",
                    results = partialResults,
                    isSearching = true,
                    searchComplete = false
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("result1.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("result2.txt").assertIsDisplayed()
        composeTestRule.onNodeWithTag("search_progress_indicator").assertIsDisplayed()
    }

    @Test
    fun search_progressiveResults_showsAllPartialResults() {
        val partialResults = listOf(
            createTestFile("document1.txt", path = "/storage/emulated/0/doc1.txt"),
            createTestFile("document2.txt", path = "/storage/emulated/0/doc2.txt"),
            createTestFile("document3.txt", path = "/storage/emulated/0/doc3.txt")
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchResultsContent(
                    query = "document",
                    results = partialResults,
                    isSearching = true,
                    searchComplete = false
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document1.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("document2.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("document3.txt").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("file_list_item").assertCountEquals(3)
    }

    @Test
    fun search_inProgress_showsCentralSpinner() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchResultsContent(
                    query = "searching",
                    results = emptyList(),
                    isSearching = true,
                    searchComplete = false
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_central_progress").assertIsDisplayed()
    }

    @Test
    fun search_complete_hidesAllSpinners() {
        val results = listOf(createTestFile("found.txt"))

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchResultsContent(
                    query = "found",
                    results = results,
                    isSearching = false,
                    searchComplete = true
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_progress_indicator").assertDoesNotExist()
        composeTestRule.onNodeWithTag("search_central_progress").assertDoesNotExist()
        composeTestRule.onNodeWithText("found.txt").assertIsDisplayed()
    }

    // ==================== Results Display Tests ====================

    @Test
    fun search_displaysCorrectNumberOfResults() {
        val results = (1..5).map {
            createTestFile("file$it.txt", path = "/storage/emulated/0/file$it.txt")
        }

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchResultsContent(
                    query = "file",
                    results = results,
                    isSearching = false,
                    searchComplete = true
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("file_list_item").assertCountEquals(5)
    }

    @Test
    fun search_displaysFileNames() {
        val results = listOf(
            createTestFile("important_document.pdf", path = "/storage/emulated/0/doc.pdf"),
            createTestFile("photo_vacation.jpg", path = "/storage/emulated/0/photo.jpg")
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchResultsContent(
                    query = "doc",
                    results = results,
                    isSearching = false,
                    searchComplete = true
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("important_document.pdf").assertIsDisplayed()
        composeTestRule.onNodeWithText("photo_vacation.jpg").assertIsDisplayed()
    }

    // ==================== State Transition Tests ====================

    @Test
    fun search_newQuery_showsSpinnerWhileSearching() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchResultsContent(
                    query = "newquery",
                    results = emptyList(),
                    isSearching = true,
                    searchComplete = false
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_central_progress").assertIsDisplayed()
    }

    @Test
    fun search_afterClear_showsEmptyState() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestSearchResultsContent(
                    query = "",
                    results = emptyList(),
                    isSearching = false,
                    searchComplete = false
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.search_no_results))
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag("search_central_progress").assertDoesNotExist()
        composeTestRule.onAllNodesWithTag("file_list_item").assertCountEquals(0)
    }

    // ==================== SearchUiState Unit Tests ====================

    @Test
    fun searchUiState_initialState_hasCorrectDefaults() {
        val state = SearchUiState()

        assertTrue("Query should be empty", state.query.isEmpty())
        assertTrue("Results should be empty", state.results.isEmpty())
        assertFalse("Should not be searching", state.isSearching)
        assertFalse("Should not be complete", state.searchComplete)
        assertFalse("Should not show no results", state.showNoResults)
    }

    @Test
    fun searchUiState_showNoResults_falseWhenEmptyQuery() {
        val state = SearchUiState(
            query = "",
            isSearching = false,
            searchComplete = true,
            results = emptyList()
        )

        assertFalse("Should not show no results for empty query", state.showNoResults)
    }

    @Test
    fun searchUiState_showNoResults_falseWhenNotComplete() {
        val state = SearchUiState(
            query = "test",
            isSearching = true,
            searchComplete = false,
            results = emptyList()
        )

        assertFalse("Should not show no results while searching", state.showNoResults)
    }

    @Test
    fun searchUiState_showNoResults_falseWhenHasResults() {
        val testFile = createTestFile("test.txt")
        val state = SearchUiState(
            query = "test",
            isSearching = false,
            searchComplete = true,
            results = listOf(testFile)
        )

        assertFalse("Should not show no results when results exist", state.showNoResults)
    }

    @Test
    fun searchUiState_showNoResults_trueWhenCompleteWithNoResults() {
        val state = SearchUiState(
            query = "nonexistent",
            isSearching = false,
            searchComplete = true,
            results = emptyList()
        )

        assertTrue("Should show no results when complete with empty results", state.showNoResults)
    }

    // ==================== Test Composable ====================

    @Composable
    private fun TestSearchResultsContent(
        query: String,
        results: List<FileItem>,
        isSearching: Boolean,
        searchComplete: Boolean
    ) {
        val showNoResults = query.isNotEmpty() && searchComplete && results.isEmpty()

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                query.isEmpty() -> {
                    // Empty state - show nothing
                }

                showNoResults -> {
                    Text(
                        text = stringResource(R.string.search_no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 120.dp)
                    )
                }

                results.isNotEmpty() -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = results,
                            key = { it.path }
                        ) { file ->
                            Box(modifier = Modifier.testTag("file_list_item")) {
                                FileListItem(
                                    file = file,
                                    isSelected = false,
                                    onClick = {},
                                    onLongClick = {},
                                    onMenuClick = {},
                                    showMenu = true
                                )
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }

                        if (isSearching) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.testTag("search_progress_indicator")
                                    )
                                }
                            }
                        }
                    }
                }

                isSearching -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 120.dp)
                            .testTag("search_central_progress")
                    )
                }
            }
        }
    }
}
