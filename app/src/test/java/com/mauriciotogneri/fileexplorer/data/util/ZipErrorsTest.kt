package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipException
import java.util.zip.ZipFile

class ZipErrorsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `isUnreadableZip returns true for missing END header ZipException`() {
        val e = ZipException("zip END header not found")
        assertTrue(isUnreadableZip(e))
    }

    @Test
    fun `isUnreadableZip returns true for any ZipException regardless of message`() {
        assertTrue(isUnreadableZip(ZipException()))
        assertTrue(isUnreadableZip(ZipException("invalid CEN header (bad signature)")))
    }

    @Test
    fun `isUnreadableZip returns true for EOFException`() {
        assertTrue(isUnreadableZip(EOFException("Unexpected end of ZLIB input stream")))
    }

    @Test
    fun `isUnreadableZip returns true for what reading an entry with cut-short data throws`() {
        // The central directory is intact, so ZipFile opens the archive; only inflating the
        // entry fails. Built by hand because ZipOutputStream always writes consistent sizes.
        val file = tempFolder.newFile("book.epub")
        file.writeBytes(zipWithCutShortEntry("OEBPS/cover.jpg"))

        try {
            ZipFile(file).use { zip ->
                zip.getInputStream(zip.getEntry("OEBPS/cover.jpg")).use { it.readBytes() }
            }
            fail("Reading an entry whose data ends early should throw")
        } catch (e: IOException) {
            assertTrue("Not classified as unreadable: $e", isUnreadableZip(e))
        }
    }

    @Test
    fun `isUnreadableZip returns false for a plain IOException`() {
        // A genuine I/O failure (not malformed ZIP content) must stay reportable.
        assertFalse(isUnreadableZip(IOException("zip END header not found")))
    }

    @Test
    fun `isUnreadableZip returns false for unrelated exceptions`() {
        assertFalse(isUnreadableZip(IllegalStateException("boom")))
        assertFalse(isUnreadableZip(RuntimeException()))
        assertFalse(isUnreadableZip(OutOfMemoryError()))
    }

    /** A one-entry archive whose headers declare only the first half of the entry's deflate stream. */
    private fun zipWithCutShortEntry(entryName: String): ByteArray {
        val content = ByteArray(200_000).also { Random(1).nextBytes(it) }
        for (i in content.indices step 3) content[i] = 0

        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        deflater.setInput(content)
        deflater.finish()
        val deflated = ByteArray(content.size * 2)
        val cutSize = deflater.deflate(deflated) / 2
        deflater.end()

        val crc = CRC32().apply { update(content) }.value.toInt()
        val name = entryName.toByteArray()
        val out = ByteBuffer.allocate(cutSize + 2 * name.size + 200).order(ByteOrder.LITTLE_ENDIAN)

        // Local file header, then the truncated data.
        out.putInt(0x04034b50).putShort(20).putShort(0).putShort(8).putInt(0)
            .putInt(crc).putInt(cutSize).putInt(content.size)
            .putShort(name.size.toShort()).putShort(0).put(name)
        out.put(deflated, 0, cutSize)

        // Central directory header, declaring the same truncated size.
        val centralOffset = out.position()
        out.putInt(0x02014b50).putShort(20).putShort(20).putShort(0).putShort(8).putInt(0)
            .putInt(crc).putInt(cutSize).putInt(content.size)
            .putShort(name.size.toShort()).putShort(0).putShort(0).putShort(0).putShort(0)
            .putInt(0).putInt(0).put(name)
        val centralSize = out.position() - centralOffset

        // End of central directory record.
        out.putInt(0x06054b50).putShort(0).putShort(0).putShort(1).putShort(1)
            .putInt(centralSize).putInt(centralOffset).putShort(0)

        return out.array().copyOf(out.position())
    }
}
