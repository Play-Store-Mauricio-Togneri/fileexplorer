package com.mauriciotogneri.fileexplorer.ui.screens.mediaviewer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.MediaTimeFormatter
import com.mauriciotogneri.fileexplorer.ui.theme.AppBarTitleStyle
import kotlinx.coroutines.delay

/** How long the controls stay over a playing fullscreen video after the last touch. */
private const val CONTROLS_HIDE_DELAY_MS = 3_000L

private val NoInsets = WindowInsets(0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    viewModel: MediaViewerViewModel,
    onBackClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val fullscreen = state.fullscreen

    // Only a fullscreen video hides its controls; everywhere else they stay on screen.
    var controlsShown by rememberSaveable { mutableStateOf(true) }
    var seeking by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        AnalyticsTracker.trackScreenMediaViewer()
    }

    // A rotation stops the Activity too, but the player outlives it and the screen stays visible.
    val activity = LocalActivity.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) viewModel.onStop()
    }

    BackHandler(enabled = fullscreen) {
        viewModel.exitFullscreen()
    }

    LaunchedEffect(fullscreen, state.playing) {
        // Leaving fullscreen or pausing brings the controls back.
        if (!fullscreen || !state.playing) controlsShown = true
    }

    LaunchedEffect(fullscreen, controlsShown, state.playing, seeking, lastTouch) {
        if (fullscreen && controlsShown && state.playing && !seeking) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsShown = false
        }
    }

    ImmersiveMode(enabled = fullscreen)
    KeepScreenOn(enabled = state.hasVideo && state.playing)

    val controls: @Composable (Modifier) -> Unit = { modifier ->
        MediaControls(
            state = state,
            onTogglePlay = {
                lastTouch = System.nanoTime()
                viewModel.togglePlay()
            },
            onSeekingChange = {
                lastTouch = System.nanoTime()
                seeking = it
            },
            onSeek = viewModel::seekTo,
            onToggleFullscreen = viewModel::toggleFullscreen,
            modifier = modifier
        )
    }

    Scaffold(
        topBar = {
            if (!fullscreen) {
                TopAppBar(
                    title = {
                        Text(
                            text = state.fileName,
                            style = AppBarTitleStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            if (!fullscreen && state.content == MediaViewerContent.Ready) {
                controls(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        },
        // A fullscreen video runs under the hidden system bars; the overlaid controls keep clear of them.
        contentWindowInsets = if (fullscreen) NoInsets else ScaffoldDefaults.contentWindowInsets,
        containerColor = if (state.hasVideo && state.content == MediaViewerContent.Ready) {
            MaterialTheme.colorScheme.scrim
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (state.content) {
                MediaViewerContent.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                MediaViewerContent.LoadError -> {
                    MediaMessage(
                        text = stringResource(R.string.media_viewer_load_error),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                MediaViewerContent.Ready -> {
                    if (state.hasVideo) {
                        VideoFrame(
                            player = viewModel.player,
                            onTap = if (fullscreen) {
                                {
                                    lastTouch = System.nanoTime()
                                    controlsShown = !controlsShown
                                }
                            } else {
                                null
                            }
                        )
                    } else {
                        AudioArtwork(modifier = Modifier.align(Alignment.Center))
                    }
                    if (fullscreen) {
                        AnimatedVisibility(
                            visible = controlsShown,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            val navigationBars = WindowInsets.navigationBars
                            val displayCutout = WindowInsets.displayCutout
                            val overlayInsets = remember(navigationBars, displayCutout) {
                                navigationBars.union(displayCutout)
                                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                            }
                            controls(Modifier.windowInsetsPadding(overlayInsets))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The video, fitted and centered; [onTap] reacts to a tap anywhere on it, bars included.
 *
 * Opted into Media3's unstable API for [ContentFrame], which keeps the frame at the video's aspect
 * ratio; the stable `PlayerSurface` would need that sizing rebuilt here. An unstable API may change
 * shape in a Media3 update, which shows up as a compile error, not at runtime.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoFrame(player: Player?, onTap: (() -> Unit)?) {
    val tapModifier = if (onTap != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onTap
        )
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(tapModifier)
    ) {
        if (player != null) {
            ContentFrame(
                player = player,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun AudioArtwork(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(160.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Audiotrack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(72.dp)
        )
    }
}

@Composable
private fun MediaControls(
    state: MediaViewerUiState,
    onTogglePlay: () -> Unit,
    onSeekingChange: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    // While the thumb is dragged, it and the time follow the finger; the seek happens on release.
    var dragPositionMs by remember { mutableStateOf<Float?>(null) }
    val durationMs = state.durationMs
    val shownPositionMs = dragPositionMs?.toLong() ?: state.positionMs
    val seekDescription = stringResource(R.string.media_viewer_seek)
    // Remembered: the time changes several times a second while playing.
    val bodyMedium = MaterialTheme.typography.bodyMedium
    val timeStyle = remember(bodyMedium) { bodyMedium.copy(fontFeatureSettings = "tnum") }

    Surface(
        color = if (state.fullscreen) {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)
        ) {
            Slider(
                value = shownPositionMs.coerceIn(0, durationMs).toFloat(),
                onValueChange = {
                    if (dragPositionMs == null) onSeekingChange(true)
                    dragPositionMs = it
                },
                onValueChangeFinished = {
                    dragPositionMs?.let { onSeek(it.toLong()) }
                    dragPositionMs = null
                    onSeekingChange(false)
                },
                valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
                enabled = durationMs > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = seekDescription }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(
                            if (state.playing) R.string.media_viewer_pause else R.string.media_viewer_play
                        )
                    )
                }
                Text(
                    text = stringResource(
                        R.string.media_viewer_position,
                        MediaTimeFormatter.format(shownPositionMs, durationMs),
                        MediaTimeFormatter.format(durationMs, durationMs)
                    ),
                    style = timeStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (state.hasVideo) {
                    IconButton(onClick = onToggleFullscreen) {
                        Icon(
                            imageVector = if (state.fullscreen) {
                                Icons.Outlined.FullscreenExit
                            } else {
                                Icons.Outlined.Fullscreen
                            },
                            contentDescription = stringResource(
                                if (state.fullscreen) {
                                    R.string.media_viewer_fullscreen_exit
                                } else {
                                    R.string.media_viewer_fullscreen_enter
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaMessage(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Hides the status and navigation bars while [enabled]; a swipe from the edge shows them briefly. */
@Composable
private fun ImmersiveMode(enabled: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(enabled, window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        if (enabled) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        view.keepScreenOn = enabled
        onDispose {
            view.keepScreenOn = false
        }
    }
}
