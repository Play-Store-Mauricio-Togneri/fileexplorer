package com.mauriciotogneri.fileexplorer.util

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [IntentUtil.openExternalLink], the one way a PDF's links leave the app. A [RecordingContext]
 * stands in for the platform so nothing real opens, and so a refused scheme can be shown to have
 * launched nothing at all rather than merely something that failed.
 */
@RunWith(AndroidJUnit4::class)
class IntentUtilExternalLinkTest {

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val launched = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            launched += intent
        }
    }

    private fun context() = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun webLink_opensAsABrowsableView() {
        val context = context()

        assertTrue(IntentUtil.openExternalLink(context, "https://example.com/a"))

        val intent = context.launched.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://example.com/a", intent.dataString)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
    }

    @Test
    fun upperCaseScheme_isNormalisedSoAppsCanMatchIt() {
        val context = context()

        assertTrue(IntentUtil.openExternalLink(context, "HTTPS://example.com/a"))
        assertEquals("https://example.com/a", context.launched.single().dataString)
    }

    @Test
    fun mailLink_opens() {
        val context = context()

        assertTrue(IntentUtil.openExternalLink(context, "mailto:someone@example.com"))
        assertEquals("mailto:someone@example.com", context.launched.single().dataString)
    }

    @Test
    fun everyOtherScheme_launchesNothing() {
        val context = context()
        listOf(
            "javascript:alert(1)",
            "file:///sdcard/Download/a.pdf",
            "content://com.example/1",
            "intent://scan/#Intent;scheme=zxing;end"
        ).forEach { url ->
            assertFalse(url, IntentUtil.openExternalLink(context, url))
        }
        assertTrue(context.launched.isEmpty())
    }
}
