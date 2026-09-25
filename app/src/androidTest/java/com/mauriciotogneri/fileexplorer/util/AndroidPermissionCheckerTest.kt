package com.mauriciotogneri.fileexplorer.util

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real [AndroidPermissionChecker], which had no test of any kind: `NavGraphTest` and
 * `PermissionScreenTest` pass a `hasPermission` boolean instead, so an inverted check here would
 * strand every user on the permission wall — or show empty lists after granting — with the suite
 * still green.
 *
 * Its two arms need different setups, because neither of the values they read is a parameter:
 *
 * - **R+ (`Environment.isExternalStorageManager`)** is an app op, granted here through `appops` the
 *   way `StartupScreenTest`, `MediaStoreUtilProviderTest` and `AndroidMediaChangeSourceTest`
 *   already do. Only the *granted* direction is exercised; see below for why.
 * - **pre-R (`ContextCompat.checkSelfPermission`)** ships to API 24-29, and no emulator this suite
 *   runs on is that low, so `hasStoragePermission()` never selects that arm. It is driven through
 *   the `hasLegacyWritePermission()` seam instead, against a [Context] whose `checkPermission`
 *   answers both ways — `ContextCompat` delegates straight to it.
 *
 * **What is not asserted.** Two things, deliberately:
 *
 * - The R+ *denied* direction. Revoking the app op sticks for the rest of the run (see the note in
 *   `NavGraphTest`) and would take every test that needs storage down with it, and the platform
 *   kills an app whose storage mount mode changes, so the revoke would abort the test process
 *   itself. Making it testable needs the `isExternalStorageManager()` probe injected as well, whose
 *   default value would then be an API-30 call lint cannot see a guard for.
 * - The branch *selection* — which arm runs on which API. That needs `Build.VERSION.SDK_INT` as a
 *   parameter, and lint's NewApi check accepts only a literal `SDK_INT` comparison as a guard, so
 *   injecting it would cost a `@SuppressLint("NewApi")` on the R+ call in production code.
 */
@RunWith(AndroidJUnit4::class)
class AndroidPermissionCheckerTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context get() = instrumentation.targetContext

    // ==================== R+ : All Files Access ====================

    @Test
    fun allFilesAccessGranted_reportsPermissionHeld() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        grantAllFilesAccess()
        // The grant is the precondition, not the assertion: what is asserted below is that
        // production reports it, which an inverted check fails whichever way the op happens to sit.
        assumeTrue(
            "All Files Access could not be granted on this device",
            Environment.isExternalStorageManager()
        )

        assertTrue(
            "All Files Access is held, so the checker must report storage permission as granted",
            AndroidPermissionChecker(context).hasStoragePermission()
        )
    }

    // ==================== pre-R : WRITE_EXTERNAL_STORAGE ====================

    @Test
    fun legacyArm_permissionGranted_reportsPermissionHeld() {
        val granting = RecordingPermissionContext(context, PackageManager.PERMISSION_GRANTED)

        assertTrue(
            "A granted write permission must report storage permission as held on API 24-29",
            AndroidPermissionChecker(granting).hasLegacyWritePermission()
        )
    }

    @Test
    fun legacyArm_permissionDenied_reportsNoPermission() {
        val denying = RecordingPermissionContext(context, PackageManager.PERMISSION_DENIED)

        assertFalse(
            "A denied write permission must report storage permission as absent on API 24-29",
            AndroidPermissionChecker(denying).hasLegacyWritePermission()
        )
    }

    /**
     * Which permission the pre-R arm asks about. The granted/denied pair above cannot catch that:
     * [RecordingPermissionContext] answers the stubbed result for whatever it is handed, so both
     * would stay green if the arm read `READ_EXTERNAL_STORAGE` — a permission that grants no write
     * access at all — instead.
     */
    @Test
    fun legacyArm_checksWriteExternalStorage() {
        val granting = RecordingPermissionContext(context, PackageManager.PERMISSION_GRANTED)

        AndroidPermissionChecker(granting).hasLegacyWritePermission()

        assertEquals(
            listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            granting.checkedPermissions
        )
    }

    // ==================== Helpers ====================

    /**
     * A context whose permission answers this test controls. `ContextCompat.checkSelfPermission`
     * delegates to `Context.checkPermission(permission, pid, uid)`, so overriding that one method
     * covers the whole call.
     */
    private class RecordingPermissionContext(
        base: Context,
        private val result: Int
    ) : ContextWrapper(base) {
        val checkedPermissions = mutableListOf<String>()

        override fun checkPermission(permission: String, pid: Int, uid: Int): Int {
            checkedPermissions += permission
            return result
        }
    }

    /**
     * The app's only storage permission above Q is All Files Access, which is an app op rather than
     * a runtime permission, so `GrantPermissionRule` cannot grant it. Mirrors the helper three other
     * classes in this suite already carry.
     */
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
