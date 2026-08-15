package com.mauriciotogneri.fileexplorer.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the [IntentUtil.openPlayStore] fallback chain: store app (`market://`) -> web URL ->
 * system chooser.
 *
 * The reported failure was a `SecurityException` on the web URL: an implicit `ACTION_VIEW` on
 * `https://play.google.com/...` let the platform choose the handler, and on the affected device
 * that was a third-party app registering those links with a non-exported Activity — a component
 * this app may not start. Addressing the store app explicitly avoids the resolution altogether,
 * and the chooser retry covers a denial on the web fallback, because it starts the target as the
 * system rather than as this app.
 *
 * A [RecordingContext] overrides `startActivity` to record each attempted intent and to inject a
 * fault per target, mirroring those devices without needing one. Espresso Intents can't drive
 * this: its stubs intercept the launch before resolution, so the primary `startActivity` never
 * throws.
 *
 * Documented assumptions:
 * - The chooser retry only happens when the device has a handler the app may actually launch, so
 *   that test is skipped on devices without a launchable `https` handler — the same guard the
 *   sibling `IntentUtilOpenFileTest` uses.
 * - The recording context never forwards to the real system, so no test opens the store or a
 *   browser. The calls run on the main thread to match the other `IntentUtil` suites, whose
 *   failure paths need a Looper for their toast.
 */
@RunWith(AndroidJUnit4::class)
class IntentUtilPlayStoreTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private class RecordingContext(
        base: Context,
        private val faults: Map<String, Exception>
    ) : ContextWrapper(base) {
        val launched = mutableListOf<Intent>()

        val targets: List<String> get() = launched.map { it.target() }

        override fun startActivity(intent: Intent) {
            launched += intent
            faults[intent.target()]?.let { throw it }
            // Success: swallow so neither the store nor a browser opens during the test.
        }

        /** The launch an intent stands for: its URI scheme, or the chooser wrapping it. */
        private fun Intent.target(): String {
            return if (action == Intent.ACTION_CHOOSER) CHOOSER else data?.scheme.orEmpty()
        }
    }

    private fun openPlayStore(vararg faults: Pair<String, Exception>): Pair<RecordingContext, Boolean> {
        val context = RecordingContext(instrumentation.targetContext, faults.toMap())
        var opened = false
        instrumentation.runOnMainSync {
            opened = IntentUtil.openPlayStore(context, APP_PACKAGE)
        }
        return context to opened
    }

    /** Whether any handler of the web store link can actually be started by this app. */
    private fun hasLaunchableWebHandler(): Boolean {
        val context = instrumentation.targetContext
        val intent = Intent(Intent.ACTION_VIEW, WEB_URL.toUri())

        return context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .any { candidate ->
                val permission = candidate.activityInfo?.permission
                permission == null ||
                    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            }
    }

    @Test
    fun storeAppInstalled_addressesStoreAppDirectly() {
        val (context, opened) = openPlayStore()

        assertTrue(opened)
        assertEquals(listOf(MARKET), context.targets)

        val intent = context.launched.single()
        assertEquals(PLAY_STORE_PACKAGE, intent.`package`)
        assertEquals(APP_PACKAGE, intent.data?.getQueryParameter("id"))
    }

    @Test
    fun storeAppMissing_fallsBackToWebUrl() {
        val (context, opened) = openPlayStore(
            MARKET to ActivityNotFoundException("no store app installed")
        )

        assertTrue(opened)
        assertEquals(listOf(MARKET, HTTPS), context.targets)
        assertEquals(WEB_URL, context.launched.last().data?.toString())
    }

    @Test
    fun webLaunchDenied_retriesThroughChooser() {
        assumeTrue("device has no launchable https handler", hasLaunchableWebHandler())

        val (context, opened) = openPlayStore(
            MARKET to ActivityNotFoundException("no store app installed"),
            HTTPS to SecurityException("Permission Denial: starting Intent, not exported")
        )

        assertTrue(opened)
        assertEquals(listOf(MARKET, HTTPS, CHOOSER), context.targets)
    }

    @Test
    fun noHandlerForEither_reportsFailureWithoutCrashing() {
        val (context, opened) = openPlayStore(
            MARKET to ActivityNotFoundException("no store app installed"),
            HTTPS to ActivityNotFoundException("no browser installed")
        )

        assertFalse(opened)
        assertEquals(listOf(MARKET, HTTPS), context.targets)
    }

    private companion object {
        const val APP_PACKAGE = "com.atomicinstinct.tensiontunnel"
        const val PLAY_STORE_PACKAGE = "com.android.vending"
        const val WEB_URL = "https://play.google.com/store/apps/details?id=$APP_PACKAGE"

        const val MARKET = "market"
        const val HTTPS = "https"
        const val CHOOSER = "chooser"
    }
}
