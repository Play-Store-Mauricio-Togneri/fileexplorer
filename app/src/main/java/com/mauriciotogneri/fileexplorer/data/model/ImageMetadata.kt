package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class ImageMetadata(
    val width: Int?,
    val height: Int?,
    val megapixels: Double?,
    val dateTaken: String?,
    val cameraMake: String?,
    val cameraModel: String?,
    val lensMake: String?,
    val lensModel: String?,
    val iso: Int?,
    val aperture: Double?,
    val focalLength: Double?,
    val exposureTime: String?,
    val flash: FlashMode?,
    val whiteBalance: WhiteBalanceMode?,
    val meteringMode: MeteringMode?,
    val sceneCaptureType: SceneCaptureType?,
    val orientation: ImageOrientation?,
    val colorSpace: ColorSpace?,
    val software: String?,
    val artist: String?,
    val copyright: String?,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val digitalZoom: Double?,
    val resolutionX: Double?,
    val resolutionY: Double?
)

enum class FlashMode {
    FIRED,
    DID_NOT_FIRE,
    ON_FIRED,
    ON_DID_NOT_FIRE,
    OFF_FIRED,
    OFF_DID_NOT_FIRE,
    AUTO_FIRED,
    AUTO_DID_NOT_FIRE
}

enum class WhiteBalanceMode {
    AUTO,
    MANUAL
}

enum class MeteringMode {
    AVERAGE,
    CENTER_WEIGHTED,
    SPOT,
    MULTI_SPOT,
    PATTERN,
    PARTIAL
}

enum class SceneCaptureType {
    STANDARD,
    LANDSCAPE,
    PORTRAIT,
    NIGHT
}

enum class ImageOrientation {
    NORMAL,
    FLIP_HORIZONTAL,
    ROTATE_180,
    FLIP_VERTICAL,
    TRANSPOSE,
    ROTATE_90_CW,
    TRANSVERSE,
    ROTATE_270_CW
}

enum class ColorSpace {
    SRGB,
    ADOBE_RGB,
    UNCALIBRATED
}
