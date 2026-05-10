package com.mauriciotogneri.fileexplorer.data.util

import androidx.exifinterface.media.ExifInterface
import com.mauriciotogneri.fileexplorer.data.model.ColorSpace
import com.mauriciotogneri.fileexplorer.data.model.FlashMode
import com.mauriciotogneri.fileexplorer.data.model.ImageMetadata
import com.mauriciotogneri.fileexplorer.data.model.ImageOrientation
import com.mauriciotogneri.fileexplorer.data.model.MeteringMode
import com.mauriciotogneri.fileexplorer.data.model.SceneCaptureType
import com.mauriciotogneri.fileexplorer.data.model.WhiteBalanceMode
import java.io.File

object ImageMetadataExtractor {

    fun extract(file: File): ImageMetadata? {
        if (!file.exists() || !file.canRead()) return null

        return try {
            val exif = ExifInterface(file.absolutePath)
            ImageMetadata(
                width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
                    ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0).takeIf { it > 0 },
                height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
                    ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0).takeIf { it > 0 },
                megapixels = calculateMegapixels(exif),
                dateTaken = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME),
                cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim(),
                cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim(),
                lensMake = exif.getAttribute(ExifInterface.TAG_LENS_MAKE)?.trim(),
                lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim(),
                iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 },
                aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 },
                focalLength = parseFocalLength(exif),
                exposureTime = formatExposureTime(exif),
                flash = parseFlash(exif),
                whiteBalance = parseWhiteBalance(exif),
                meteringMode = parseMeteringMode(exif),
                sceneCaptureType = parseSceneCaptureType(exif),
                orientation = parseOrientation(exif),
                colorSpace = parseColorSpace(exif),
                software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.trim(),
                artist = exif.getAttribute(ExifInterface.TAG_ARTIST)?.trim(),
                copyright = exif.getAttribute(ExifInterface.TAG_COPYRIGHT)?.trim(),
                latitude = exif.latLong?.get(0),
                longitude = exif.latLong?.get(1),
                altitude = exif.getAltitude(Double.NaN).takeIf { !it.isNaN() },
                digitalZoom = exif.getAttributeDouble(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, 0.0).takeIf { it > 0 },
                resolutionX = exif.getAttributeDouble(ExifInterface.TAG_X_RESOLUTION, 0.0).takeIf { it > 0 },
                resolutionY = exif.getAttributeDouble(ExifInterface.TAG_Y_RESOLUTION, 0.0).takeIf { it > 0 }
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateMegapixels(exif: ExifInterface): Double? {
        val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
            ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0).takeIf { it > 0 }
        val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
            ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0).takeIf { it > 0 }

        return if (width != null && height != null) {
            (width.toLong() * height.toLong()) / 1_000_000.0
        } else {
            null
        }
    }

    private fun parseFocalLength(exif: ExifInterface): Double? {
        val rational = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH) ?: return null
        return try {
            val parts = rational.split("/")
            if (parts.size == 2) {
                parts[0].toDouble() / parts[1].toDouble()
            } else {
                rational.toDoubleOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatExposureTime(exif: ExifInterface): String? {
        val exposure = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
        if (exposure <= 0) return null

        return if (exposure >= 1) {
            "${exposure}s"
        } else {
            val denominator = (1 / exposure).toLong()
            "1/${denominator}s"
        }
    }

    private fun parseFlash(exif: ExifInterface): FlashMode? {
        val flash = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
        if (flash < 0) return null

        val fired = (flash and 0x01) != 0
        val mode = (flash shr 3) and 0x03

        return when (mode) {
            1 -> if (fired) FlashMode.ON_FIRED else FlashMode.ON_DID_NOT_FIRE
            2 -> if (fired) FlashMode.OFF_FIRED else FlashMode.OFF_DID_NOT_FIRE
            3 -> if (fired) FlashMode.AUTO_FIRED else FlashMode.AUTO_DID_NOT_FIRE
            else -> if (fired) FlashMode.FIRED else FlashMode.DID_NOT_FIRE
        }
    }

    private fun parseWhiteBalance(exif: ExifInterface): WhiteBalanceMode? {
        return when (exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, -1)) {
            ExifInterface.WHITE_BALANCE_AUTO.toInt() -> WhiteBalanceMode.AUTO
            ExifInterface.WHITE_BALANCE_MANUAL.toInt() -> WhiteBalanceMode.MANUAL
            else -> null
        }
    }

    private fun parseMeteringMode(exif: ExifInterface): MeteringMode? {
        return when (exif.getAttributeInt(ExifInterface.TAG_METERING_MODE, -1)) {
            1 -> MeteringMode.AVERAGE
            2 -> MeteringMode.CENTER_WEIGHTED
            3 -> MeteringMode.SPOT
            4 -> MeteringMode.MULTI_SPOT
            5 -> MeteringMode.PATTERN
            6 -> MeteringMode.PARTIAL
            else -> null
        }
    }

    private fun parseSceneCaptureType(exif: ExifInterface): SceneCaptureType? {
        return when (exif.getAttributeInt(ExifInterface.TAG_SCENE_CAPTURE_TYPE, -1)) {
            0 -> SceneCaptureType.STANDARD
            1 -> SceneCaptureType.LANDSCAPE
            2 -> SceneCaptureType.PORTRAIT
            3 -> SceneCaptureType.NIGHT
            else -> null
        }
    }

    private fun parseOrientation(exif: ExifInterface): ImageOrientation? {
        return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
            ExifInterface.ORIENTATION_NORMAL -> ImageOrientation.NORMAL
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ImageOrientation.FLIP_HORIZONTAL
            ExifInterface.ORIENTATION_ROTATE_180 -> ImageOrientation.ROTATE_180
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> ImageOrientation.FLIP_VERTICAL
            ExifInterface.ORIENTATION_TRANSPOSE -> ImageOrientation.TRANSPOSE
            ExifInterface.ORIENTATION_ROTATE_90 -> ImageOrientation.ROTATE_90_CW
            ExifInterface.ORIENTATION_TRANSVERSE -> ImageOrientation.TRANSVERSE
            ExifInterface.ORIENTATION_ROTATE_270 -> ImageOrientation.ROTATE_270_CW
            else -> null
        }
    }

    private fun parseColorSpace(exif: ExifInterface): ColorSpace? {
        return when (exif.getAttributeInt(ExifInterface.TAG_COLOR_SPACE, -1)) {
            1 -> ColorSpace.SRGB
            2 -> ColorSpace.ADOBE_RGB
            0xFFFF -> ColorSpace.UNCALIBRATED
            else -> null
        }
    }
}
