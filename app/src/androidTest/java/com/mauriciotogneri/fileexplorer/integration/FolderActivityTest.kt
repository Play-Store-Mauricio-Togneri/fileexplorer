package com.mauriciotogneri.fileexplorer.integration

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.activities.FolderActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Back behavior of the standalone [FolderActivity]: at the launch folder the internal NavHost has
 * nothing to pop, so a system back finishes the Activity (returning to whatever launched it).
 * Deeper folder-to-folder navigation pops within the NavHost instead and is exercised by the
 * folder navigation tests.
 */
@RunWith(AndroidJUnit4::class)
class FolderActivityTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(context.cacheDir, "folderact_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun systemBackAtLaunchFolder_finishesActivity() {
        val intent = FolderActivity.createIntent(context, testDir.absolutePath)
        ActivityScenario.launch<FolderActivity>(intent).use { scenario ->
            // The dispatcher's fallback finishes the Activity synchronously, so capture
            // isFinishing on the main thread right after the back press. Asserting the terminal
            // DESTROYED state instead would race the async onPause -> onStop -> onDestroy teardown.
            val finishing = AtomicBoolean(false)
            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
                finishing.set(activity.isFinishing)
            }

            assertTrue("System back at the launch folder should finish the Activity", finishing.get())
        }
    }
}
