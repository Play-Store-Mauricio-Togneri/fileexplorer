package com.mauriciotogneri.fileexplorer.integration

import android.os.Build
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The premise three whole test classes rest on, asserted instead of assumed.
 *
 * `StartupScreenTest`, `MediaStoreUtilProviderTest` and `AndroidMediaChangeSourceTest` each grant
 * themselves All Files Access with the same `appops set ... MANAGE_EXTERNAL_STORAGE allow` recipe
 * and then `assumeTrue(Environment.isExternalStorageManager())`. That guard is right on its own
 * terms — a test cannot fail for something it was never able to arrange — but it cannot say so. On
 * an image whose appops refuses the shell command, or where the op does not land inside the
 * two-second poll, all three classes skip every test they own and the run still reports green,
 * having exercised no startup-folder navigation, no MediaStore path query and no media-change
 * observer registration.
 *
 * This is the same shape as `FileRepositoryTest.the fixture filesystem enforces a write denial`:
 * one named failure that points at the environment, instead of three classes' worth of invisible
 * skips. It asserts nothing about the app — only that the run is able to set up what those classes
 * need.
 *
 * The grant helper is duplicated from `StartupScreenTest` rather than shared. Hoisting it would
 * mean editing all three classes to depend on a new seam, and the point of this file is to be
 * readable on its own when it is the only thing red.
 */
@RunWith(AndroidJUnit4::class)
class AllFilesAccessPremiseTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun theRunCanGrantItselfAllFilesAccess() {
        // Below R the permission does not exist and the three classes skip for a reason that is
        // documented rather than accidental, so there is no premise to check.
        assumeTrue(
            "All Files Access only exists from R",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        )

        grantAllFilesAccess()

        assertTrue(
            "`appops set MANAGE_EXTERNAL_STORAGE allow` did not take on this image, so " +
                "StartupScreenTest, MediaStoreUtilProviderTest and AndroidMediaChangeSourceTest " +
                "will each skip every test they own while the run reports green. Grant All Files " +
                "Access to the app under test, or use an image whose appops accepts the command.",
            Environment.isExternalStorageManager()
        )
    }

    private fun grantAllFilesAccess() {
        instrumentation.uiAutomation.executeShellCommand(
            "appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow"
        ).close()
        // The shell command runs in another process; give the op a moment to land.
        repeat(POLL_ATTEMPTS) {
            if (Environment.isExternalStorageManager()) return
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    private companion object {
        const val POLL_ATTEMPTS = 20
        const val POLL_INTERVAL_MS = 100L
    }
}
