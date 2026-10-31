package com.mauriciotogneri.fileexplorer.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcel
import android.widget.Toast
import androidx.core.content.FileProvider
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The failures in [IntentUtil] that report no failure event, and so must block the rating ask
 * themselves: without the direct call, the rating dialog could follow a share, an install, or a
 * screen that just failed to open in front of the user.
 *
 * For the share and the APK install, the content URI is the step made to fail, since it is the
 * first thing both paths do. The share's other refusal — a selection too large for one Binder
 * transaction — is reached by stubbing the Parcel that measures it: the test pins what the refusal
 * does, while the measurement itself needs a real Parcel and is left to the device.
 *
 * For the screens, the launch is the step made to fail: the device has no Activity for it. The
 * JVM's android.jar throws on every framework method, so [Uri] and the setters the paths call on an
 * [Intent] are stubbed to let each path reach its launch, and every such test checks that it did.
 */
class IntentUtilReviewPromptFailureTest {

    private val context = mockk<Context>(relaxed = true)

    private val apk = FileItem(
        path = "/storage/emulated/0/Download/app.apk",
        name = "app.apk",
        isDirectory = false,
        size = 1024,
        lastModified = 0,
        createdTime = 0,
        mimeType = "application/vnd.android.package-archive"
    )

    @Before
    fun setUp() {
        mockkObject(ReviewPrompt)
        mockkObject(ErrorReporter)
        every { ErrorReporter.warning(any(), any(), any()) } just Runs

        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(any(), any(), any<File>()) } throws
            IllegalArgumentException("Failed to find configured root")

        mockkStatic(Toast::class)
        every { Toast.makeText(any<Context>(), any<Int>(), any()) } returns mockk(relaxed = true)

        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().setType(any()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().addFlags(any()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().addCategory(any()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } returns mockk(relaxed = true)

        mockkStatic(Intent::class)
        every { Intent.createChooser(any(), any()) } returns mockk(relaxed = true)

        every { context.startActivity(any()) } throws ActivityNotFoundException()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `an APK whose path cannot be shared with the installer blocks the rating ask`() {
        IntentUtil.installApk(context, apk, "folder")

        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
    }

    @Test
    fun `a share that fails blocks the rating ask`() {
        IntentUtil.shareFiles(context, listOf(apk))

        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `an empty share is not a failure`() {
        IntentUtil.shareFiles(context, emptyList())

        verify(exactly = 0) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `a selection too large to share blocks the rating ask`() {
        every { FileProvider.getUriForFile(any(), any(), any<File>()) } returns mockk()
        every { anyConstructed<Intent>().putParcelableArrayListExtra(any(), any()) } returns mockk(relaxed = true)
        mockkStatic(Parcel::class)
        every { Parcel.obtain() } returns mockk<Parcel>(relaxed = true) {
            every { dataSize() } returns Int.MAX_VALUE
        }
        val files = listOf(apk, apk.copy(path = "/storage/emulated/0/Download/other.apk", name = "other.apk"))

        assertFalse(IntentUtil.shareFiles(context, files))

        verify(exactly = 0) { context.startActivity(any()) }
        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `an install permission screen that cannot open blocks the rating ask`() {
        IntentUtil.openInstallPermissionSettings(context)

        verify { context.startActivity(any()) }
        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    // The last step of the all-files-access fallbacks as well, so this covers every way they end.
    @Test
    fun `an app details screen that cannot open blocks the rating ask`() {
        // `false` also tells the permission screen that no trip to Settings began.
        assertFalse(IntentUtil.openAppDetailsSettings(context))

        verify { context.startActivity(any()) }
        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `a store page that cannot open blocks the rating ask`() {
        assertFalse(IntentUtil.openPlayStore(context, "com.example.app"))

        verify { context.startActivity(any()) }
        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `an app share that cannot open blocks the rating ask`() {
        assertFalse(IntentUtil.shareApp(context))

        verify { context.startActivity(any()) }
        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `a link that cannot open blocks the rating ask`() {
        assertFalse(IntentUtil.openExternalLink(context, "https://example.com"))

        verify { context.startActivity(any()) }
        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }
}
