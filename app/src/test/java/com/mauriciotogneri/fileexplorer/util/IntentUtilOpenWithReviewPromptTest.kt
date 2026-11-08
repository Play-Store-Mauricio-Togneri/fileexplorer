package com.mauriciotogneri.fileexplorer.util

import com.mauriciotogneri.fileexplorer.util.IntentUtil.HandlerAvailability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards which finished "Open with" counts towards the rating ask.
 *
 * The system chooser launches whether or not any app can take the file, so the launch alone would
 * count an empty picker — a dead end — as a success, and could ask for a rating right after it.
 * Only the handler query tells the two apart, and the query runs in the background where nothing
 * the user sees depends on it, so a wrong rule here would go unnoticed.
 */
class IntentUtilOpenWithReviewPromptTest {

    private fun counts(availability: HandlerAvailability, opened: Boolean): Boolean {
        return IntentUtil.openWithCountsTowardsReviewPrompt(availability, opened)
    }

    @Test
    fun launchedChooserWithAHandler_counts() {
        assertTrue(counts(HandlerAvailability.AVAILABLE, opened = true))
    }

    @Test
    fun chooserThatDidNotLaunch_doesNotCount() {
        assertFalse(counts(HandlerAvailability.AVAILABLE, opened = false))
    }

    @Test
    fun noHandler_doesNotCount_evenWhenTheChooserLaunched() {
        assertFalse(counts(HandlerAvailability.NONE, opened = true))
        assertFalse(counts(HandlerAvailability.NONE, opened = false))
    }

    @Test
    fun failedQuery_doesNotCount_evenWhenTheChooserLaunched() {
        assertFalse(counts(HandlerAvailability.UNKNOWN, opened = true))
        assertFalse(counts(HandlerAvailability.UNKNOWN, opened = false))
    }
}
