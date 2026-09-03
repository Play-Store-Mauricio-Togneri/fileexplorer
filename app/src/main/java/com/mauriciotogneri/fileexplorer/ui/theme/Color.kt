package com.mauriciotogneri.fileexplorer.ui.theme

import androidx.compose.ui.graphics.Color

// Primary colors
val primaryLight = Color(0xFF606060)
val primaryDark = Color(0xFF909090)

val onPrimaryLight = Color(0xFFFDFBFF)
val onPrimaryDark = Color(0xFF1A1C1E)

val primaryContainerLight = Color(0xFF909090)
val primaryContainerDark = Color(0xFF505050)

val onPrimaryContainerLight = Color(0xFF1A1C1E)
val onPrimaryContainerDark = Color(0xFF909090)

// Background colors
val backgroundLight = Color(0xFFFDFBFF)
val backgroundDark = Color(0xFF1A1C1E)

val onBackgroundLight = Color(0xFF1A1C1E)
val onBackgroundDark = Color(0xFFE2E2E6)

// Surface colors
val surfaceLight = Color(0xFFFBFBFB)
val surfaceDark = Color(0xFF1A1C1E)

val surfaceContainerLowLight = Color(0xFFF0F0F0)
val surfaceContainerLowDark = Color(0xFF2A2C2E)

val surfaceContainerLight = Color(0xFFF0F0F0)
val surfaceContainerDark = Color(0xFF2A2C2E)

val surfaceContainerHighLight = Color(0xFFF0F0F0)
val surfaceContainerHighDark = Color(0xFF2A2C2E)

val onSurfaceLight = Color(0xFF202020)
val onSurfaceDark = Color(0xFFE2E2E6)

val surfaceVariantLight = Color(0xFFD5D5D5)
val surfaceVariantDark = Color(0xFF2F3033)

val onSurfaceVariantLight = Color(0xFF404040)
val onSurfaceVariantDark = Color(0xFFE2E2E6)

val outlineVariantLight = Color(0xFFC0C0C0)
val outlineVariantDark = Color(0xFF404040)

// Selection colors
val selectionBackgroundLight = Color(0xFFDCDCDC)
val selectionBackgroundDark = Color(0xFF323436)

// Error colors
val errorLight = Color(0xFFD05A5A)
val errorDark = Color(0xFFFFB4AB)

val onErrorLight = Color(0xFFFDFBFF)
val onErrorDark = Color(0xFF3A3D41)

val errorContainerLight = Color(0xFFFFB4AB)
val errorContainerDark = Color(0xFFD05A5A)

val onErrorContainerLight = Color(0xFF1A1C1E)
val onErrorContainerDark = Color(0xFFFFB4AB)

// Storage analyzer category tones
//
// The app has no categorical palette because it has no colour: introducing six hues for one chart
// would make the analyzer the only screen in the app that is not greyscale. These are a luminance
// ramp instead, one step per category in declaration order, and each category's row draws its own
// progress bar in its own tone — so the list is the chart's legend and no swatch column is needed.
//
// The span is not a free choice. These tones are the *fill* of a progress bar whose track is
// surfaceVariant, so the faintest one still has to clear 3:1 against that track (WCAG 1.4.11, the
// bar for a graphical object you have to see to read the value). That fixes the far end at about
// #747474 in light and #828386 in dark and leaves six evenly spaced steps to reach it. Being merely
// "on the same side of" the track is not enough — the last category is SYSTEM, which absorbs every
// app and every directory the walk cannot open and is routinely the largest slice, so it is the one
// that must not read as an empty bar.
//
// Measured against their tracks: light 7.75 / 6.43 / 5.41 / 4.49 / 3.80 / 3.18, dark
// 10.21 / 8.56 / 7.03 / 5.62 / 4.47 / 3.48.
val categoryTonesLight = listOf(
    Color(0xFF3A3A3A),
    Color(0xFF464646),
    Color(0xFF515151),
    Color(0xFF5D5D5D),
    Color(0xFF686868),
    Color(0xFF747474)
)

val categoryTonesDark = listOf(
    Color(0xFFE2E2E6),
    Color(0xFFCFD0D4),
    Color(0xFFBCBDC1),
    Color(0xFFA8A9AD),
    Color(0xFF95969A),
    Color(0xFF828386)
)
