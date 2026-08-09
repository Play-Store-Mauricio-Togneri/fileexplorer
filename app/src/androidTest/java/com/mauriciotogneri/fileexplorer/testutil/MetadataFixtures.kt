package com.mauriciotogneri.fileexplorer.testutil

import com.mauriciotogneri.fileexplorer.data.model.ApkMetadata
import com.mauriciotogneri.fileexplorer.data.model.AudioChannels
import com.mauriciotogneri.fileexplorer.data.model.AudioMetadata
import com.mauriciotogneri.fileexplorer.data.model.ColorSpace
import com.mauriciotogneri.fileexplorer.data.model.FlashMode
import com.mauriciotogneri.fileexplorer.data.model.ImageMetadata
import com.mauriciotogneri.fileexplorer.data.model.ImageOrientation
import com.mauriciotogneri.fileexplorer.data.model.MeteringMode
import com.mauriciotogneri.fileexplorer.data.model.SceneCaptureType
import com.mauriciotogneri.fileexplorer.data.model.VideoColorStandard
import com.mauriciotogneri.fileexplorer.data.model.VideoColorTransfer
import com.mauriciotogneri.fileexplorer.data.model.VideoMetadata
import com.mauriciotogneri.fileexplorer.data.model.WhiteBalanceMode
import com.mauriciotogneri.fileexplorer.data.model.ZipMetadata

/**
 * Builders for the metadata bags rendered by `ItemInfoContent`.
 *
 * The production data classes declare every field without a default, deliberately: an extractor must
 * decide what it did and did not find. Tests are the opposite case — they set one or two fields and
 * need the rest absent — so the defaults live here rather than loosening the production API.
 */
object MetadataFixtures {

    fun image(
        width: Int? = null,
        height: Int? = null,
        megapixels: Double? = null,
        dateTaken: String? = null,
        cameraMake: String? = null,
        cameraModel: String? = null,
        lensMake: String? = null,
        lensModel: String? = null,
        iso: Int? = null,
        aperture: Double? = null,
        focalLength: Double? = null,
        exposureTime: String? = null,
        flash: FlashMode? = null,
        whiteBalance: WhiteBalanceMode? = null,
        meteringMode: MeteringMode? = null,
        sceneCaptureType: SceneCaptureType? = null,
        orientation: ImageOrientation? = null,
        colorSpace: ColorSpace? = null,
        software: String? = null,
        artist: String? = null,
        copyright: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        altitude: Double? = null,
        digitalZoom: Double? = null,
        resolutionX: Double? = null,
        resolutionY: Double? = null
    ) = ImageMetadata(
        width = width,
        height = height,
        megapixels = megapixels,
        dateTaken = dateTaken,
        cameraMake = cameraMake,
        cameraModel = cameraModel,
        lensMake = lensMake,
        lensModel = lensModel,
        iso = iso,
        aperture = aperture,
        focalLength = focalLength,
        exposureTime = exposureTime,
        flash = flash,
        whiteBalance = whiteBalance,
        meteringMode = meteringMode,
        sceneCaptureType = sceneCaptureType,
        orientation = orientation,
        colorSpace = colorSpace,
        software = software,
        artist = artist,
        copyright = copyright,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        digitalZoom = digitalZoom,
        resolutionX = resolutionX,
        resolutionY = resolutionY
    )

    fun audio(
        duration: Long? = null,
        artist: String? = null,
        album: String? = null,
        title: String? = null,
        genre: String? = null,
        year: String? = null,
        bitrate: Int? = null,
        trackNumber: String? = null,
        discNumber: String? = null,
        composer: String? = null,
        albumArtist: String? = null,
        writer: String? = null,
        sampleRate: Int? = null,
        bitDepth: Int? = null,
        channels: AudioChannels? = null,
        isCompilation: Boolean? = null,
        recordingDate: String? = null
    ) = AudioMetadata(
        duration = duration,
        artist = artist,
        album = album,
        title = title,
        genre = genre,
        year = year,
        bitrate = bitrate,
        trackNumber = trackNumber,
        discNumber = discNumber,
        composer = composer,
        albumArtist = albumArtist,
        writer = writer,
        sampleRate = sampleRate,
        bitDepth = bitDepth,
        channels = channels,
        isCompilation = isCompilation,
        recordingDate = recordingDate
    )

    fun video(
        duration: Long? = null,
        width: Int? = null,
        height: Int? = null,
        frameRate: Float? = null,
        bitrate: Int? = null,
        rotation: Int? = null,
        colorStandard: VideoColorStandard? = null,
        colorTransfer: VideoColorTransfer? = null,
        audioSampleRate: Int? = null,
        audioBitDepth: Int? = null,
        title: String? = null,
        dateRecorded: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        author: String? = null
    ) = VideoMetadata(
        duration = duration,
        width = width,
        height = height,
        frameRate = frameRate,
        bitrate = bitrate,
        rotation = rotation,
        colorStandard = colorStandard,
        colorTransfer = colorTransfer,
        audioSampleRate = audioSampleRate,
        audioBitDepth = audioBitDepth,
        title = title,
        dateRecorded = dateRecorded,
        latitude = latitude,
        longitude = longitude,
        author = author
    )

    fun apk(
        packageName: String? = null,
        appName: String? = null,
        versionName: String? = null,
        versionCode: Long? = null,
        minSdk: Int? = null,
        targetSdk: Int? = null,
        permissions: List<String>? = null
    ) = ApkMetadata(
        packageName = packageName,
        appName = appName,
        versionName = versionName,
        versionCode = versionCode,
        minSdk = minSdk,
        targetSdk = targetSdk,
        permissions = permissions
    )

    fun zip(
        entryCount: Int? = null,
        compressedSize: Long? = null,
        uncompressedSize: Long? = null
    ) = ZipMetadata(
        entryCount = entryCount,
        compressedSize = compressedSize,
        uncompressedSize = uncompressedSize
    )
}
