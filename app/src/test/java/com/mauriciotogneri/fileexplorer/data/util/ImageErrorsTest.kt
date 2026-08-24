package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class ImageErrorsTest {

    @Test
    fun `isUndecodableImage returns true for Coil's null bitmap decode failure`() {
        // The exact IllegalStateException Coil's BitmapFactoryDecoder throws for undecodable content.
        val e = IllegalStateException(
            "BitmapFactory returned a null bitmap. Often this means BitmapFactory could not " +
                "decode the image data read from the input source (e.g. network, disk, or memory) " +
                "as it's not encoded as a valid image format."
        )
        assertTrue(isUndecodableImage(e))
    }

    @Test
    fun `isUndecodableImage returns true for Coil's GIF decode failure`() {
        // The exact IllegalStateException coil.decode.GifDecoder's check() throws for a corrupt GIF.
        // Below API 28 that decoder replaces ImageDecoderDecoder, whose equivalent failure is an
        // IOException; matching both keeps reporting independent of the API level.
        assertTrue(isUndecodableImage(IllegalStateException("Failed to decode GIF.")))
    }

    @Test
    fun `isUndecodableImage matches when the phrase is wrapped by other text`() {
        assertTrue(isUndecodableImage(IllegalStateException("Decode failed: BitmapFactory returned a null bitmap")))
    }

    @Test
    fun `isUndecodableImage returns false for an IllegalStateException with a different message`() {
        // A bare-type match would swallow unrelated decoder failures; only the specific message counts.
        assertFalse(isUndecodableImage(IllegalStateException("closed")))
        assertFalse(isUndecodableImage(IllegalStateException()))
    }

    @Test
    fun `isUndecodableImage returns false for the same message on a different type`() {
        assertFalse(isUndecodableImage(RuntimeException("BitmapFactory returned a null bitmap")))
        assertFalse(isUndecodableImage(RuntimeException("Failed to decode GIF.")))
    }

    @Test
    fun `isUndecodableImage returns false for unrelated exceptions`() {
        assertFalse(isUndecodableImage(FileNotFoundException()))
        assertFalse(isUndecodableImage(IOException()))
        assertFalse(isUndecodableImage(IllegalArgumentException()))
        assertFalse(isUndecodableImage(OutOfMemoryError()))
    }
}
