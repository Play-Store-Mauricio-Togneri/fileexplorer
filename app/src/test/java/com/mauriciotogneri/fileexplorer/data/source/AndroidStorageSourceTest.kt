package com.mauriciotogneri.fileexplorer.data.source

import android.content.Context
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.StorageType
import com.mauriciotogneri.fileexplorer.data.util.VolumeInfo
import com.mauriciotogneri.fileexplorer.data.util.VolumeStats
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Covers what [AndroidStorageSource] does with whatever `getExternalFilesDirs` hands it: the
 * derivation from an app-private directory to the volume it sits on, the deduplication the card
 * list depends on, and the numbering of the labels.
 *
 * The volume sizes and the framework's own view of each volume are injected rather than read.
 * Reaching any of the above through the real
 * [com.mauriciotogneri.fileexplorer.data.util.volumeStatsAt] is impossible here — it constructs a
 * `StatFs`, which the unit-test `android.jar` cannot build, so every volume would be dropped before
 * a single one of these assertions had anything to look at, and
 * [com.mauriciotogneri.fileexplorer.data.util.volumeInfoAt] needs a `StorageManager` the same jar
 * cannot produce.
 */
class AndroidStorageSourceTest {

    @Test
    fun `getStorages reports nothing when the framework reports no volume`() = runTest {
        val source = sourceWith(contextWith())

        val result = source.getStorages()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getStorages resolves an app directory to the volume it sits on`() = runTest {
        val source = sourceWith(contextWith(appDirOn(PRIMARY)))

        val result = source.getStorages()

        assertEquals(1, result.size)
        assertEquals(PRIMARY, result[0].path)
        assertEquals(INTERNAL_LABEL, result[0].displayName)
        assertEquals(TOTAL_BYTES, result[0].totalBytes)
        assertEquals(AVAILABLE_BYTES, result[0].availableBytes)
    }

    @Test
    fun `getStorages reports an sd card alongside internal storage`() = runTest {
        val source = sourceWith(contextWith(appDirOn(PRIMARY), appDirOn(SD_CARD)))

        val result = source.getStorages()

        assertEquals(listOf(PRIMARY, SD_CARD), result.map { it.path })
        assertEquals(listOf(INTERNAL_LABEL, SD_CARD_LABEL), result.map { it.displayName })
    }

    @Test
    fun `getStorages collapses entries that resolve to the same volume`() = runTest {
        // Some devices report the same storage root twice. A duplicate reaches the destination
        // picker's lazy list as a repeated key, which crashes Compose measurement, so the
        // deduplication is load-bearing rather than cosmetic.
        val source = sourceWith(contextWith(appDirOn(PRIMARY), appDirOn(PRIMARY)))

        val result = source.getStorages()

        assertEquals(listOf(PRIMARY), result.map { it.path })
    }

    @Test
    fun `getStorages ignores a volume the framework reported as null`() = runTest {
        // getExternalFilesDirs nulls an entry rather than omitting it when it cannot give this app
        // a directory on that volume, so the array is not dense.
        val source = sourceWith(contextWith(appDirOn(PRIMARY), null, appDirOn(SD_CARD)))

        val result = source.getStorages()

        assertEquals(listOf(PRIMARY, SD_CARD), result.map { it.path })
    }

    @Test
    fun `getStorages drops a volume whose size cannot be read`() = runTest {
        val source = sourceWith(
            contextWith(appDirOn(PRIMARY), appDirOn(SD_CARD)),
            stats = unreadableAt(SD_CARD)
        )

        val result = source.getStorages()

        assertEquals(listOf(PRIMARY), result.map { it.path })
    }

    @Test
    fun `getStorages numbers volumes that would otherwise share a name`() = runTest {
        val source = sourceWith(
            contextWith(
                appDirOn(PRIMARY),
                appDirOn(SECOND_INTERNAL),
                appDirOn(SD_CARD),
                appDirOn(SECOND_SD_CARD)
            )
        )

        val result = source.getStorages()

        assertEquals(
            listOf("$INTERNAL_LABEL 1", "$INTERNAL_LABEL 2", "$SD_CARD_LABEL 1", "$SD_CARD_LABEL 2"),
            result.map { it.displayName }
        )
    }

    @Test
    fun `getStorages numbers only the volumes that share a name`() = runTest {
        val source = sourceWith(
            contextWith(appDirOn(PRIMARY), appDirOn(SD_CARD), appDirOn(SECOND_SD_CARD))
        )

        val result = source.getStorages()

        assertEquals(
            listOf(INTERNAL_LABEL, "$SD_CARD_LABEL 1", "$SD_CARD_LABEL 2"),
            result.map { it.displayName }
        )
    }

    @Test
    fun `getStorages keeps a directory that does not end in the app directory suffix`() = runTest {
        // The suffix is what identifies the volume root, and a path that does not carry it is left
        // exactly as it came. Deriving a root from it anyway would invent a path this app then
        // treats as a storage root — and getStorages() is what supplies the allowed roots every
        // copy, move and compress target is checked against.
        val unexpected = File("/storage/ABCD-1234/Android/obb/$PACKAGE")
        val source = sourceWith(contextWith(unexpected))

        val result = source.getStorages()

        assertEquals(listOf(unexpected.absolutePath), result.map { it.path })
    }

    @Test
    fun `getStorages names a removable volume the way the framework describes it`() = runTest {
        // The framework is the only place a removable volume's kind is recorded — no public API
        // reports it — so the name has to be taken from there rather than derived from the path.
        val source = sourceWith(
            contextWith(appDirOn(PRIMARY), appDirOn(SD_CARD)),
            info = described(SD_CARD to USB_DESCRIPTION)
        )

        val result = source.getStorages()

        assertEquals(listOf(INTERNAL_LABEL, USB_DESCRIPTION), result.map { it.displayName })
    }

    @Test
    fun `getStorages falls back to the SD card name when the framework describes nothing`() = runTest {
        // The name every removable volume carried before the description was consulted, so a
        // device that shows a card today keeps the name it had rather than losing one.
        val source = sourceWith(
            contextWith(appDirOn(PRIMARY), appDirOn(SD_CARD)),
            info = described(SD_CARD to "  ")
        )

        val result = source.getStorages()

        assertEquals(listOf(INTERNAL_LABEL, SD_CARD_LABEL), result.map { it.displayName })
    }

    @Test
    fun `getStorages keeps this app own name for internal storage`() = runTest {
        // The framework's description is in the system's locale and this app's string is
        // translated, so the description is only consulted where it carries information this app
        // has no other way to get.
        val source = sourceWith(
            contextWith(appDirOn(PRIMARY)),
            info = described(PRIMARY to "Internal shared storage")
        )

        val result = source.getStorages()

        assertEquals(listOf(INTERNAL_LABEL), result.map { it.displayName })
    }

    @Test
    fun `getStorages types a volume the framework calls removable as a card`() = runTest {
        val source = sourceWith(contextWith(appDirOn(PRIMARY), appDirOn(SD_CARD)))

        val result = source.getStorages()

        assertEquals(listOf(StorageType.INTERNAL, StorageType.SD_CARD), result.map { it.type })
    }

    @Test
    fun `getStorages numbers two volumes the framework gave the same name`() = runTest {
        val source = sourceWith(
            contextWith(appDirOn(SD_CARD), appDirOn(SECOND_SD_CARD)),
            info = described(SD_CARD to USB_DESCRIPTION, SECOND_SD_CARD to USB_DESCRIPTION)
        )

        val result = source.getStorages()

        assertEquals(
            listOf("$USB_DESCRIPTION 1", "$USB_DESCRIPTION 2"),
            result.map { it.displayName }
        )
    }

    @Test
    fun `getStorages takes removability from the framework rather than from the path`() = runTest {
        // A volume the framework calls emulated is this device's own storage whatever its path
        // looks like — and naming that one from a description would leave it untranslated.
        val source = sourceWith(
            contextWith(appDirOn(SD_CARD)),
            info = { VolumeInfo(isEmulated = true, description = "Internal shared storage") }
        )

        val result = source.getStorages()

        assertEquals(listOf(StorageType.INTERNAL), result.map { it.type })
        assertEquals(listOf(INTERNAL_LABEL), result.map { it.displayName })
    }

    private fun contextWith(vararg dirs: File?): Context {
        val externalDirs: Array<File?> = arrayOf(*dirs)

        return mockk {
            every { packageName } returns PACKAGE
            every { getExternalFilesDirs(null) } returns externalDirs
            every { getString(R.string.storage_internal) } returns INTERNAL_LABEL
            every { getString(R.string.storage_sd_card) } returns SD_CARD_LABEL
        }
    }

    private fun appDirOn(volumeRoot: String) = File("$volumeRoot/Android/data/$PACKAGE/files")

    /**
     * Every case states what the framework knows, rather than leaving it to a default: the fallback
     * for a framework that says nothing is itself behaviour under test here.
     */
    private fun sourceWith(
        context: Context,
        stats: (String) -> VolumeStats? = allVolumesReadable,
        info: (String) -> VolumeInfo? = { null }
    ) = AndroidStorageSource(context, stats, info)

    private fun described(vararg descriptions: Pair<String, String?>): (String) -> VolumeInfo? {
        val byPath = descriptions.toMap()
        return { path ->
            byPath[path]?.let {
                VolumeInfo(isEmulated = path.contains("emulated"), description = it)
            }
        }
    }

    private val allVolumesReadable: (String) -> VolumeStats? =
        { VolumeStats(totalBytes = TOTAL_BYTES, availableBytes = AVAILABLE_BYTES) }

    private fun unreadableAt(vararg volumeRoots: String): (String) -> VolumeStats? = { path ->
        if (path in volumeRoots) null else VolumeStats(TOTAL_BYTES, AVAILABLE_BYTES)
    }

    private companion object {
        const val PACKAGE = "com.mauriciotogneri.fileexplorer"
        const val PRIMARY = "/storage/emulated/0"
        const val SECOND_INTERNAL = "/storage/emulated/10"
        const val SD_CARD = "/storage/1234-5678"
        const val SECOND_SD_CARD = "/storage/ABCD-EF01"
        const val INTERNAL_LABEL = "Internal Storage"
        const val SD_CARD_LABEL = "SD Card"
        const val USB_DESCRIPTION = "USB drive"
        const val TOTAL_BYTES = 32_000_000_000L
        const val AVAILABLE_BYTES = 16_000_000_000L
    }
}
