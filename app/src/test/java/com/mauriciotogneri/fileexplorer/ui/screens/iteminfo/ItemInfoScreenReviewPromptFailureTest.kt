package com.mauriciotogneri.fileexplorer.ui.screens.iteminfo

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.util.ReviewPrompt
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Opening a photo's location on a device with no map app shows a toast and nothing else. No failure
 * event reports it, so it must block the rating ask itself — as a file no app can open already does.
 *
 * The JVM's android.jar throws on every framework method, so [Uri] is stubbed to let the path reach
 * its launch, and the test checks that it did.
 */
class ItemInfoScreenReviewPromptFailureTest {

    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(ReviewPrompt, AnalyticsTracker, ErrorReporter)
        every { AnalyticsTracker.trackItemInfoOpenMaps() } just Runs
        every { ErrorReporter.error(any(), any(), any()) } just Runs

        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)

        mockkStatic(Toast::class)
        every { Toast.makeText(any<Context>(), any<CharSequence>(), any()) } returns mockk(relaxed = true)

        every { context.startActivity(any()) } throws ActivityNotFoundException()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `a location no map app can open blocks the rating ask`() {
        openGeoUri(context, 48.8584, 2.2945, "No app can show this location")

        verify { context.startActivity(any()) }
        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }
}
