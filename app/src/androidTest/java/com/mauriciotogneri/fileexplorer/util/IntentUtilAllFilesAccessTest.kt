package com.mauriciotogneri.fileexplorer.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the [IntentUtil.openAllFilesAccessSettings] fallback chain that fixes the
 * `ActivityNotFoundException` crash: per-app deep-link -> global all-files-access list ->
 * app details page -> toast.
 *
 * Espresso-Intents can't drive this — its stubs intercept the launch *before* resolution, so the
 * primary `startActivity` never throws. Instead a [RecordingContext] overrides `startActivity` to
 * record each attempted intent and throw `ActivityNotFoundException` for chosen actions, mirroring
 * ROMs/emulators that lack a handler. No production change is required.
 *
 * Documented assumptions:
 * - The action constants are compile-time `String`s, and the recording context never forwards to
 *   the real system, so the chain is API-independent (no R+ gate needed) and never opens Settings.
 * - The all-fallbacks-failed branch shows a Toast (needs a Looper) and calls
 *   `ErrorReporter.warning` (needs FirebaseApp initialised by the target app). The call therefore
 *   runs on the main thread; the test asserts all three intents were attempted in order and that
 *   the final exception is swallowed — it does not assert the toast UI (flaky to observe).
 */
@RunWith(AndroidJUnit4::class)
class IntentUtilAllFilesAccessTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private class RecordingContext(
        base: Context,
        private val failActions: Set<String>
    ) : ContextWrapper(base) {
        val launchedActions = mutableListOf<String?>()

        override fun startActivity(intent: Intent) {
            launchedActions += intent.action
            if (intent.action in failActions) {
                throw ActivityNotFoundException("no handler for ${intent.action}")
            }
            // Success: swallow so the real Settings screen never opens during the test.
        }
    }

    private fun openAllFilesAccess(failActions: Set<String>): RecordingContext {
        val context = RecordingContext(instrumentation.targetContext, failActions)
        // Run on the main thread: the final fallback shows a Toast, which requires a Looper.
        instrumentation.runOnMainSync {
            IntentUtil.openAllFilesAccessSettings(context)
        }
        return context
    }

    @Test
    fun perAppScreenAvailable_launchesPerAppIntentOnly() {
        val context = openAllFilesAccess(failActions = emptySet())

        assertEquals(
            listOf(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION),
            context.launchedActions
        )
    }

    @Test
    fun perAppScreenMissing_fallsBackToGlobalAllFilesList() {
        val context = openAllFilesAccess(
            failActions = setOf(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        )

        assertEquals(
            listOf(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
            ),
            context.launchedActions
        )
    }

    @Test
    fun allFilesScreensMissing_fallsBackToAppDetails() {
        val context = openAllFilesAccess(
            failActions = setOf(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
            )
        )

        assertEquals(
            listOf(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            ),
            context.launchedActions
        )
    }

    @Test
    fun allScreensMissing_attemptsAllThenReportsWithoutCrashing() {
        val context = openAllFilesAccess(
            failActions = setOf(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            )
        )

        // All three intents attempted in order; the final ActivityNotFoundException is swallowed
        // (ErrorReporter.warning + toast), so openAllFilesAccessSettings returns without throwing.
        assertEquals(
            listOf(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            ),
            context.launchedActions
        )
    }
}
