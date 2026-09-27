package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MimeTypeUtilIsViewableMediaTest {

    @Test
    fun `audio containers Media3 extracts are viewable`() {
        assertTrue(MimeTypeUtil.isViewableAudio("audio/mpeg", "a.mp3"))
        assertTrue(MimeTypeUtil.isViewableAudio("audio/mp4", "a.m4a"))
        assertTrue(MimeTypeUtil.isViewableAudio("audio/aac", "a.aac"))
        assertTrue(MimeTypeUtil.isViewableAudio("audio/ogg", "a.ogg"))
        assertTrue(MimeTypeUtil.isViewableAudio("audio/x-wav", "a.wav"))
        assertTrue(MimeTypeUtil.isViewableAudio("audio/flac", "a.flac"))
        assertTrue(MimeTypeUtil.isViewableAudio("audio/amr", "a.amr"))
    }

    @Test
    fun `video containers Media3 extracts are viewable`() {
        assertTrue(MimeTypeUtil.isViewableVideo("video/mp4", "a.mp4"))
        assertTrue(MimeTypeUtil.isViewableVideo("video/quicktime", "a.mov"))
        assertTrue(MimeTypeUtil.isViewableVideo("video/3gpp", "a.3gp"))
        assertTrue(MimeTypeUtil.isViewableVideo("video/webm", "a.webm"))
        assertTrue(MimeTypeUtil.isViewableVideo("video/x-matroska", "a.mkv"))
        assertTrue(MimeTypeUtil.isViewableVideo("video/mpeg", "a.mpg"))
        assertTrue(MimeTypeUtil.isViewableVideo("video/x-flv", "a.flv"))
        assertTrue(MimeTypeUtil.isViewableVideo("video/x-msvideo", "a.avi"))
    }

    @Test
    fun `extension alone qualifies when the mime type is unknown`() {
        // FileItem.getMimeType returns "*/*" for types Android cannot resolve; the extension set
        // is the fallback signal.
        assertTrue(MimeTypeUtil.isViewableAudio("*/*", "voice.opus"))
        assertTrue(MimeTypeUtil.isViewableVideo("*/*", "clip.mkv"))
    }

    @Test
    fun `mime type alone qualifies when the extension is unknown`() {
        assertTrue(MimeTypeUtil.isViewableAudio("audio/mpeg", "track"))
        assertTrue(MimeTypeUtil.isViewableVideo("video/mp4", "clip"))
    }

    @Test
    fun `extension matching is case-insensitive`() {
        assertTrue(MimeTypeUtil.isViewableAudio("*/*", "SONG.MP3"))
        assertTrue(MimeTypeUtil.isViewableVideo("*/*", "Clip.Mp4"))
    }

    @Test
    fun `audio containers without a Media3 extractor are not viewable`() {
        assertFalse(MimeTypeUtil.isViewableAudio("audio/x-ms-wma", "a.wma"))
        assertFalse(MimeTypeUtil.isViewableAudio("audio/midi", "a.mid"))
        assertFalse(MimeTypeUtil.isViewableAudio("audio/x-pn-realaudio", "a.ra"))
        assertFalse(MimeTypeUtil.isViewableAudio("audio/x-aiff", "a.aiff"))
    }

    @Test
    fun `video containers without a Media3 extractor are not viewable`() {
        assertFalse(MimeTypeUtil.isViewableVideo("video/x-ms-wmv", "a.wmv"))
        assertFalse(MimeTypeUtil.isViewableVideo("video/x-ms-asf", "a.asf"))
        assertFalse(MimeTypeUtil.isViewableVideo("application/vnd.rn-realmedia", "a.rm"))
        // Ogg's extractor reads no video track, so a Theora file would not play.
        assertFalse(MimeTypeUtil.isViewableVideo("video/ogg", "a.ogv"))
    }

    @Test
    fun `typescript sources are not viewable video`() {
        // "ts" and "mts" are MPEG-TS extensions too, but TEXT_EXTENSIONS claims them for TypeScript.
        assertFalse(MimeTypeUtil.isViewableVideo("*/*", "index.ts"))
        assertFalse(MimeTypeUtil.isViewableVideo("*/*", "index.mts"))
    }

    @Test
    fun `audio and video do not cross over`() {
        assertFalse(MimeTypeUtil.isViewableAudio("video/x-matroska", "a.mkv"))
        assertFalse(MimeTypeUtil.isViewableVideo("audio/x-matroska", "a.mka"))
    }

    @Test
    fun `non-media content is not viewable`() {
        assertFalse(MimeTypeUtil.isViewableAudio("text/plain", "a.txt"))
        assertFalse(MimeTypeUtil.isViewableVideo("image/png", "a.png"))
        assertFalse(MimeTypeUtil.isViewableAudio("*/*", "README"))
        assertFalse(MimeTypeUtil.isViewableVideo("*/*", "README"))
    }
}
