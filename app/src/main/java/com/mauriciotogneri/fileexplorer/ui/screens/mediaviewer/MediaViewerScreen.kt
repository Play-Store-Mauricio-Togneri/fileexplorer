package com.mauriciotogneri.fileexplorer.ui.screens.mediaviewer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.thumbnailCacheKeyAtSize
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.AppImageLoader
import com.mauriciotogneri.fileexplorer.data.util.MediaTimeFormatter
import com.mauriciotogneri.fileexplorer.ui.components.ViewerMessage
import com.mauriciotogneri.fileexplorer.ui.theme.AppBarTitleStyle
import com.mauriciotogneri.fileexplorer.ui.theme.TrackTitleStyle
import kotlinx.coroutines.delay
import java.io.File

/** How long the controls stay over a playing fullscreen video after the last touch. */
private const val CONTROLS_HIDE_DELAY_MS = 3_000L

private val NoInsets = WindowInsets(0)

/** The seek bar's round thumb, drawn only while it is dragged; it sizes the track's inset either way. */
private val SeekThumbSize = 12.dp

/**
 * The seek bar's height, set by its thumb slot: short, so the text above and below sits close to the
 * line. Compose still hands it touches up to [SeekTouchOverhang] beyond it, but only within the
 * controls' own surface, which clips touches at its edges.
 */
private val SeekBarHeight = 24.dp

/** How far past the seek bar its 48dp minimum touch target reaches, above and below. */
private val SeekTouchOverhang = (48.dp - SeekBarHeight) / 2

/** The space between the seek bar and the text above and below it, on top of the bar's own margin. */
private val SeekBarTextGap = 6.dp

/** How much of the unplayed track's color is left while the length is unknown and seeking is off. */
private const val DISABLED_TRACK_ALPHA = 0.38f

/**
 * How far the seek bar's track starts from the edges of the slider: the slider keeps half a thumb
 * clear at each end, so the text above and below lines up with the track's ends through this.
 */
private val SeekTrackInset = SeekThumbSize / 2

private val SeekTrackHeight = 6.dp

/** The largest the cover art is drawn; it shrinks to fit a shorter body, as in landscape. */
private val ArtworkMaxSize = 280.dp

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
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onStart()
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
            onToggleMute = {
                lastTouch = System.nanoTime()
                viewModel.toggleMute()
            },
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
                    ViewerMessage(
                        icon = Icons.Outlined.ErrorOutline,
                        text = stringResource(R.string.media_viewer_load_error),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                MediaViewerContent.Ready -> {
                    if (state.hasVideo) {
                        VideoFrame(
                            player = viewModel.player,
                            // Fullscreen, a tap shows or hides the controls; otherwise it plays or pauses.
                            onTap = if (fullscreen) {
                                {
                                    lastTouch = System.nanoTime()
                                    controlsShown = !controlsShown
                                }
                            } else {
                                viewModel::togglePlay
                            },
                            onTapLabel = if (fullscreen) null else stringResource(
                                if (state.playing) R.string.media_viewer_pause else R.string.media_viewer_play
                            )
                        )
                    } else {
                        // A tap anywhere around the artwork plays or pauses, like the button.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClickLabel = stringResource(
                                        if (state.playing) R.string.media_viewer_pause else R.string.media_viewer_play
                                    ),
                                    onClick = viewModel::togglePlay
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AudioArtwork(
                                filePath = viewModel.filePath,
                                cacheKey = state.artworkCacheKey
                            )
                        }
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
 * The video, fitted and centered; [onTap] reacts to a tap anywhere on it, bars included, and
 * [onTapLabel] names what it does for accessibility services.
 *
 * Opted into Media3's unstable API for [ContentFrame], which keeps the frame at the video's aspect
 * ratio; the stable `PlayerSurface` would need that sizing rebuilt here. An unstable API may change
 * shape in a Media3 update, which shows up as a compile error, not at runtime.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoFrame(player: Player?, onTap: () -> Unit, onTapLabel: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = onTapLabel,
                onClick = onTap
            )
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

/**
 * The cover art embedded in the file, loaded through the same thumbnail pipeline as the folder
 * list; a file without one shows a generic audio icon instead. Nothing is drawn while it loads, so
 * a file with art does not flash the icon first.
 */
@Composable
private fun AudioArtwork(filePath: String, cacheKey: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val requestSizePx = with(LocalDensity.current) { ArtworkMaxSize.roundToPx() }
    Box(
        modifier = modifier
            .padding(24.dp)
            .sizeIn(maxWidth = ArtworkMaxSize, maxHeight = ArtworkMaxSize)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        if (cacheKey != null) {
            val request = remember(filePath, cacheKey, requestSizePx) {
                ImageRequest.Builder(context)
                    .data(File(filePath))
                    .memoryCacheKey(thumbnailCacheKeyAtSize(cacheKey, requestSizePx))
                    .size(requestSizePx)
                    .crossfade(true)
                    .build()
            }
            SubcomposeAsyncImage(
                model = request,
                imageLoader = AppImageLoader.thumbnails(context),
                // Decorative: the file name is in the app bar.
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                success = {
                    SubcomposeAsyncImageContent(
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                },
                error = {
                    AudioIcon()
                }
            )
        }
    }
}

@Composable
private fun AudioIcon() {
    Box(
        modifier = Modifier
            // Coil hands its error slot the whole artwork square as a minimum size.
            .wrapContentSize()
            .size(160.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Audiotrack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaControls(
    state: MediaViewerUiState,
    onTogglePlay: () -> Unit,
    onSeekingChange: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onToggleMute: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    // While the thumb is dragged, it and the time follow the finger; the seek happens on release.
    var dragPositionMs by remember { mutableStateOf<Float?>(null) }
    val durationMs = state.durationMs
    val seekable = durationMs != null && durationMs > 0
    // A video's own tags would only take height from the picture.
    val showTrackInfo = !state.hasVideo && (state.title != null || state.artist != null)
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
                // Audio has nothing beside the play button, so its text gets more room from the edges.
                .padding(horizontal = if (state.hasVideo) 12.dp else 24.dp)
                // With nothing above the seek bar, room for the top of its touch target, which a tap
                // would otherwise pass through to the picture behind: playing, pausing, or hiding these.
                .padding(top = if (showTrackInfo) 4.dp else SeekTouchOverhang, bottom = 8.dp)
        ) {
            if (showTrackInfo) {
                TrackInfo(
                    title = state.title,
                    artist = state.artist,
                    modifier = Modifier.padding(horizontal = SeekTrackInset)
                )
            }
            // Laid out at the thumb's height rather than the 48dp minimum, which would keep the text
            // far from the line; the column's padding keeps the touch target inside the surface.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Slider(
                    value = shownPositionMs.coerceIn(0, durationMs ?: 0).toFloat(),
                    onValueChange = {
                        if (dragPositionMs == null) onSeekingChange(true)
                        dragPositionMs = it
                    },
                    onValueChangeFinished = {
                        dragPositionMs?.let { onSeek(it.toLong()) }
                        dragPositionMs = null
                        onSeekingChange(false)
                    },
                    valueRange = 0f..(durationMs ?: 0).coerceAtLeast(1).toFloat(),
                    enabled = seekable,
                    thumb = {
                        SeekThumb(visible = dragPositionMs != null)
                    },
                    track = { sliderState ->
                        SeekTrack(sliderState, enabled = seekable)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = seekDescription }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SeekTrackInset)
                    .padding(top = SeekBarTextGap),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    // Until the length is known, shaped as if it were short: most files are.
                    text = MediaTimeFormatter.format(shownPositionMs, durationMs ?: 0),
                    style = timeStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Nothing until the length is known: "00:00" would read as an empty file.
                if (durationMs != null) {
                    Text(
                        text = MediaTimeFormatter.format(durationMs, durationMs),
                        style = timeStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (state.hasVideo) {
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = if (state.muted) {
                                Icons.AutoMirrored.Outlined.VolumeOff
                            } else {
                                Icons.AutoMirrored.Outlined.VolumeUp
                            },
                            contentDescription = stringResource(
                                if (state.muted) R.string.media_viewer_unmute else R.string.media_viewer_mute
                            )
                        )
                    }
                }
                FilledIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(64.dp)
                ) {
                    // Filled, unlike the app's other icons: an outlined play arrow is a hollow triangle.
                    Icon(
                        imageVector = if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (state.playing) R.string.media_viewer_pause else R.string.media_viewer_play
                        ),
                        modifier = Modifier.size(36.dp)
                    )
                }
                if (state.hasVideo) {
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
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

/**
 * The seek bar's thumb: kept at its size even while hidden, because the slider insets the track by
 * half its width, and a thumb that appeared on touch would shorten the track under the finger. Its
 * height sets the seek bar's, which the slider centers the track in; the circle sits in its middle.
 */
@Composable
private fun SeekThumb(visible: Boolean) {
    Box(
        modifier = Modifier.size(width = SeekThumbSize, height = SeekBarHeight),
        contentAlignment = Alignment.Center
    ) {
        if (visible) {
            Box(
                modifier = Modifier
                    .size(SeekThumbSize)
                    .background(MaterialTheme.colorScheme.onSurface, CircleShape)
            )
        }
    }
}

/**
 * A thin rounded line: the part played in `onSurface`, ending under the thumb's center, and the
 * rest in `outlineVariant`, faded while the slider is disabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekTrack(sliderState: SliderState, enabled: Boolean) {
    val activeColor = MaterialTheme.colorScheme.onSurface
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant.let {
        if (enabled) it else it.copy(alpha = DISABLED_TRACK_ALPHA)
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(SeekTrackHeight)
    ) {
        val corner = CornerRadius(size.height / 2)
        drawRoundRect(color = inactiveColor, cornerRadius = corner)
        // The slider places the thumb's center this far along the track.
        val played = size.width * sliderState.coercedValueAsFraction
        if (played > 0f) {
            // Played from the start edge, which is the right one in a right-to-left layout.
            val left = if (layoutDirection == LayoutDirection.Rtl) size.width - played else 0f
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(left, 0f),
                size = Size(played, size.height),
                cornerRadius = corner
            )
        }
    }
}

/** The title and artist from the file's tags, each only when it names one. */
@Composable
private fun TrackInfo(title: String?, artist: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = SeekBarTextGap)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = TrackTitleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (artist != null) {
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
