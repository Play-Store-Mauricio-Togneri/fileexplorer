package com.mauriciotogneri.fileexplorer.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

/**
 * Duration of the navigation transitions below. Nothing renders differently at any point during
 * them, so this is not a visual choice: the value only has to stay non-zero, and above the ~9ms
 * threshold at which an interrupted transition can round its own remaining duration back down to
 * zero. One frame at 60Hz.
 *
 * It must never be zero: `NavHost` always drives its transition through a `SeekableTransitionState`,
 * which divides by the transition's total duration on every frame of a navigation. That division is
 * only guarded by a `playTime >= duration` check, so with a zero duration a single frame carrying a
 * non-monotonic timestamp (some OEM frame schedulers emit them) yields -Infinity, then NaN, and
 * kills the process with `IllegalArgumentException: Cannot round NaN value`.
 */
private const val INSTANT_TRANSITION_MS = 16

/**
 * Instant enter transition: animates alpha from 1f to 1f, so a destination renders exactly as it
 * would with no transition at all.
 *
 * Must not be replaced with [EnterTransition.None], which leaves the transition with no animations
 * and therefore a zero duration (see [INSTANT_TRANSITION_MS]), nor given a spring spec: a spring
 * derives its duration from the displacement, and 1f -> 1f has none, which puts the duration back
 * at zero.
 */
internal val InstantEnter: EnterTransition = fadeIn(
    initialAlpha = 1f,
    animationSpec = tween(durationMillis = INSTANT_TRANSITION_MS)
)

/**
 * Instant exit transition; the counterpart of [InstantEnter].
 *
 * Must not be replaced with [ExitTransition.None]; see [INSTANT_TRANSITION_MS].
 */
internal val InstantExit: ExitTransition = fadeOut(
    targetAlpha = 1f,
    animationSpec = tween(durationMillis = INSTANT_TRANSITION_MS)
)
