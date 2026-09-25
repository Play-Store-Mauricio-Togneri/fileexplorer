package com.mauriciotogneri.fileexplorer.data.util

import coil3.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the gate every thumbnail fetcher opens with.
 *
 * Coil maps the [java.io.File] a request carries to a `file://` [Uri] before any fetcher is
 * consulted, so [toFileOrNull] is what decides whether a video, PDF, APK, audio or EPUB thumbnail
 * is extracted at all. A regression here shows up as those files quietly falling back to a generic
 * icon rather than as a failure, which is why it is pinned here rather than left to the
 * instrumentation tests that drive whole loads.
 *
 * Uris are built the way Coil's own `FileMapper` builds them — through the structured constructor,
 * not by parsing a string — so what these assert is the round trip production actually performs.
 */
class FileUriTest {

    @Test
    fun `a file uri resolves to its path`() {
        assertEquals("/storage/emulated/0/clip.mp4", fileUri("/storage/emulated/0/clip.mp4").toFileOrNull()?.path)
    }

    @Test
    fun `a path keeping characters a url would escape survives intact`() {
        // Nothing percent-encodes on the way in, so nothing may percent-decode on the way out: a
        // file whose name holds a #, a ? or a space is a different file from the decoded form, and
        // resolving to the wrong one would thumbnail somebody else's file.
        val path = "/storage/emulated/0/Camping #2 (50% off) ? notes ünïcode 🎬.mp4"

        assertEquals(path, fileUri(path).toFileOrNull()?.path)
    }

    @Test
    fun `a content uri is left to coil`() {
        assertNull(Uri(scheme = "content", authority = "media", path = "/external/video/media/12").toFileOrNull())
    }

    @Test
    fun `a resource uri is left to coil`() {
        assertNull(Uri(scheme = "android.resource", authority = "com.example", path = "/drawable/ic").toFileOrNull())
    }

    @Test
    fun `a uri with no scheme is not treated as a file`() {
        // Coil's own isFileUri treats a scheme-less Uri as a file. Nothing in this app produces
        // one — every request carries a File, which Coil maps with an explicit file scheme — so
        // the narrower gate is deliberate, and this pins it rather than leaving it to chance.
        assertNull(Uri(path = "/storage/emulated/0/clip.mp4").toFileOrNull())
    }

    @Test
    fun `a file uri with no path resolves to nothing`() {
        assertNull(Uri(scheme = "file").toFileOrNull())
    }

    private fun fileUri(path: String): Uri = Uri(scheme = "file", path = path)
}
