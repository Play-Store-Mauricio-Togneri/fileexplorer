package com.mauriciotogneri.fileexplorer.data.util

import com.mauriciotogneri.fileexplorer.data.model.VideoColorStandard
import com.mauriciotogneri.fileexplorer.data.model.VideoColorTransfer
import com.mauriciotogneri.fileexplorer.data.model.VideoMetadata
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class VideoMetadataExtractorTest {

    private val empty = VideoMetadata(
        duration = null,
        width = null,
        height = null,
        frameRate = null,
        bitrate = null,
        rotation = null,
        colorStandard = null,
        colorTransfer = null,
        audioSampleRate = null,
        audioBitDepth = null,
        title = null,
        dateRecorded = null,
        latitude = null,
        longitude = null,
        author = null
    )

    @Test
    fun `empty metadata is rejected`() {
        assertNull(empty.nullIfEmpty())
    }

    @Test
    fun `each metadata field is sufficient to preserve a partial result`() {
        val partialResults = listOf(
            empty.copy(duration = 1000L),
            empty.copy(width = 160),
            empty.copy(height = 120),
            empty.copy(frameRate = 30f),
            empty.copy(bitrate = 800),
            empty.copy(rotation = 90),
            empty.copy(colorStandard = VideoColorStandard.BT709),
            empty.copy(colorTransfer = VideoColorTransfer.SDR),
            empty.copy(audioSampleRate = 44100),
            empty.copy(audioBitDepth = 16),
            empty.copy(title = "Fixture video"),
            empty.copy(dateRecorded = "20260919"),
            empty.copy(latitude = 47.0),
            empty.copy(longitude = 8.0),
            empty.copy(author = "Fixture author")
        )

        partialResults.forEach { metadata ->
            assertSame("Preserve partial metadata: $metadata", metadata, metadata.nullIfEmpty())
        }
    }

    @Test
    fun `zero numeric values are not missing metadata`() {
        val zeroResults = listOf(
            empty.copy(duration = 0L),
            empty.copy(width = 0),
            empty.copy(height = 0),
            empty.copy(frameRate = 0f),
            empty.copy(bitrate = 0),
            empty.copy(rotation = 0),
            empty.copy(audioSampleRate = 0),
            empty.copy(audioBitDepth = 0),
            empty.copy(latitude = 0.0),
            empty.copy(longitude = 0.0)
        )

        zeroResults.forEach { metadata ->
            assertSame("Preserve zero metadata: $metadata", metadata, metadata.nullIfEmpty())
        }
    }
}
