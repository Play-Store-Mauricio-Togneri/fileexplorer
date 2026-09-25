package com.mauriciotogneri.fileexplorer.ui.screens.pdfviewer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import com.mauriciotogneri.fileexplorer.data.model.PdfSearchMatch
import com.mauriciotogneri.fileexplorer.data.util.PdfViewerMode
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.mauriciotogneri.fileexplorer.data.model.PdfRectPt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.PdfPageSize
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.PdfViewerSupport
import com.mauriciotogneri.fileexplorer.ui.components.DeleteConfirmDialog
import com.mauriciotogneri.fileexplorer.ui.components.PdfPasswordDialog
import com.mauriciotogneri.fileexplorer.ui.theme.AppBarTitleStyle
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/** Zoom a double tap goes to from 1x. */
private const val DOUBLE_TAP_ZOOM = 2.5f

/** Settle time before re-rendering the visible region sharp, so a quick follow-up gesture wins. */
private const val REGION_RENDER_DELAY_MS = 120L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    viewModel: PdfViewerViewModel,
    onBackClick: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val zoom = remember { PdfZoomState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showGoToPage by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AnalyticsTracker.trackScreenPdfViewer()
    }

    BackHandler(enabled = state.search.active) {
        viewModel.closeSearch()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PdfViewerUiEvent.Finish -> onFinish()
                is PdfViewerUiEvent.ShowToast ->
                    Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
                is PdfViewerUiEvent.ScrollToPage -> {
                    val pageSizes = (viewModel.state.value.content as? PdfViewerContent.Loaded)?.pageSizes
                    val pageSize = pageSizes?.getOrNull(event.page)
                    if (pageSize != null) {
                        scrollToPage(listState, zoom, event.page, pageSize, event.area)
                    }
                }
                is PdfViewerUiEvent.OpenExternalLink -> {
                    if (!IntentUtil.openExternalLink(context, event.url)) {
                        Toast.makeText(context, R.string.pdf_viewer_link_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (state.search.active) {
                PdfSearchBar(
                    search = state.search,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onSearch = viewModel::submitSearch,
                    onClose = viewModel::closeSearch,
                    onPrevious = viewModel::previousMatch,
                    onNext = viewModel::nextMatch
                )
            } else {
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
                    actions = {
                        // Search exists only where the renderer can find text.
                        if (state.mode == PdfViewerMode.FULL && state.content is PdfViewerContent.Loaded) {
                            IconButton(onClick = viewModel::openSearch) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = stringResource(R.string.pdf_viewer_search)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            PdfViewerActionBar(
                shareEnabled = state.file != null,
                onShare = {
                    state.file?.let {
                        viewModel.onShareClicked()
                        IntentUtil.shareFiles(context, listOf(it))
                    }
                },
                onDelete = { showDeleteConfirm = true }
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val content = state.content) {
                is PdfViewerContent.PasswordRequired -> Unit
                is PdfViewerContent.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is PdfViewerContent.LoadError -> {
                    PdfMessage(
                        icon = Icons.Outlined.PictureAsPdf,
                        text = stringResource(R.string.pdf_viewer_load_error),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is PdfViewerContent.PasswordUnsupported -> {
                    PdfMessage(
                        icon = Icons.Outlined.Lock,
                        text = stringResource(R.string.pdf_viewer_password_unsupported),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is PdfViewerContent.Loaded -> {
                    PdfDocument(
                        pageSizes = content.pageSizes,
                        search = state.search,
                        listState = listState,
                        zoom = zoom,
                        viewModel = viewModel
                    )
                    PageIndicator(
                        listState = listState,
                        zoom = zoom,
                        pageCount = content.pageSizes.size,
                        onClick = { showGoToPage = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                }
            }
        }
    }

    (state.content as? PdfViewerContent.PasswordRequired)?.let { request ->
        PdfPasswordDialog(
            wrongPassword = request.wrongPassword,
            onCancel = viewModel::cancelPassword,
            onOpen = viewModel::submitPassword
        )
    }

    val loaded = state.content as? PdfViewerContent.Loaded
    if (showGoToPage && loaded != null) {
        GoToPageDialog(
            pageCount = loaded.pageSizes.size,
            onDismiss = { showGoToPage = false },
            onGo = { page ->
                showGoToPage = false
                viewModel.goToPage(page - 1)
            }
        )
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            itemCount = 1,
            itemName = state.fileName,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                viewModel.onDeleteConfirmed()
            }
        )
    }
}

@Composable
private fun PdfDocument(
    pageSizes: List<PdfPageSize>,
    search: PdfSearchState,
    listState: LazyListState,
    zoom: PdfZoomState,
    viewModel: PdfViewerViewModel
) {
    val matchesByPage = remember(search.matches) { search.matches.groupBy { it.page } }
    val currentMatch = search.matches.getOrNull(search.currentIndex)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // The list is drawn scaled; without the clip a zoomed page would paint over the bars.
            .clipToBounds()
            .onSizeChanged { zoom.viewport = it }
            .pointerInput(Unit) { detectZoomAndPan(zoom, listState) }
            .pointerInput(pageSizes) {
                detectTapGestures(
                    onDoubleTap = { point ->
                        val target = if (zoom.scale > 1f) 1f else DOUBLE_TAP_ZOOM
                        zoom.zoomTo(target, point, listState)
                    },
                    onTap = { point ->
                        pageAt(listState, zoom, pageSizes, point)?.let { (page, xPt, yPt) ->
                            viewModel.onPageTapped(page, xPt, yPt)
                        }
                    }
                )
            }
    ) {
        val pageWidthPx = constraints.maxWidth
        // Pages read the scale itself only when they render or draw, so a pinch — a new scale every
        // frame — recomposes nothing; this flips twice per zoom-in-and-out.
        val zoomedIn by remember { derivedStateOf { zoom.scale > 1f } }
        // Where each visible page meets the screen, in the page's own unzoomed pixels — computed
        // only once the document is still and zoomed, which is when a sharp re-render pays off.
        val visibleRegions by remember(pageWidthPx) {
            derivedStateOf {
                val settled = zoom.scale > 1f && !zoom.gestureActive && !listState.isScrollInProgress
                if (settled) visibleRegions(listState, zoom, pageWidthPx) else emptyMap()
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offsetX
                    translationY = zoom.offsetY
                },
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(count = pageSizes.size, key = { it }) { index ->
                PdfPage(
                    index = index,
                    pageSize = pageSizes[index],
                    pageWidthPx = pageWidthPx,
                    zoomedIn = zoomedIn,
                    zoom = zoom::scale,
                    visibleRegion = visibleRegions[index],
                    matches = matchesByPage[index].orEmpty(),
                    currentMatch = currentMatch?.takeIf { it.page == index },
                    viewModel = viewModel
                )
            }
        }
    }
}

/** The page under screen point [screen], with the point in that page's own points. */
private fun pageAt(
    listState: LazyListState,
    zoom: PdfZoomState,
    pageSizes: List<PdfPageSize>,
    screen: Offset
): Triple<Int, Float, Float>? {
    val local = zoom.toLocal(screen)
    val info = listState.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { item ->
        val top = item.offset - info.viewportStartOffset
        local.y >= top && local.y < top + item.size
    } ?: return null
    val pageSize = pageSizes.getOrNull(item.index) ?: return null
    val ptPerPx = pageSize.width / zoom.viewport.width.toFloat()
    val top = item.offset - info.viewportStartOffset
    return Triple(item.index, local.x * ptPerPx, (local.y - top) * ptPerPx)
}

private fun visibleRegions(listState: LazyListState, zoom: PdfZoomState, pageWidthPx: Int): Map<Int, Rect> {
    val band = zoom.visibleLocalRect()
    val info = listState.layoutInfo
    return info.visibleItemsInfo.mapNotNull { item ->
        // Item offsets count from the end of the top content padding; local space from the top.
        val top = (item.offset - info.viewportStartOffset).toFloat()
        val page = Rect(0f, top, pageWidthPx.toFloat(), top + item.size)
        val visible = page.intersect(band)
        if (visible.width <= 0f || visible.height <= 0f) null else item.index to visible.translate(0f, -top)
    }.toMap()
}

/**
 * Brings [page] into view — [area] on it, when given, a quarter of the way down the screen — keeping
 * the current zoom.
 */
private suspend fun scrollToPage(
    listState: LazyListState,
    zoom: PdfZoomState,
    page: Int,
    pageSize: PdfPageSize,
    area: PdfRectPt?
) {
    val pxPerPt = zoom.viewport.width.toFloat() / pageSize.width
    zoom.alignTop()
    var offset = 0
    if (area != null) {
        val visibleHeight = zoom.viewport.height / zoom.scale
        offset = (area.top * pxPerPt - visibleHeight / 4f).roundToInt().coerceAtLeast(0)
        zoom.centerOn((area.left + area.right) / 2f * pxPerPt)
    }
    listState.animateScrollToItem(page, offset)
    if (zoom.scale <= 1f) return
    // Near the end of the document the list stops short of the requested offset, leaving the target
    // below the zoomed band; finish the move with the zoom offset.
    val info = listState.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == page } ?: return
    val pageTop = (item.offset - info.viewportStartOffset).toFloat()
    if (area != null) {
        zoom.placeAt(pageTop + area.top * pxPerPt, zoom.viewport.height / 4f)
    } else {
        zoom.placeAt(pageTop, 0f)
    }
}

/** A sharp render of [area], in the page's unzoomed pixels. */
private class RegionImage(val image: ImageBitmap, val area: Rect)

/** A page as it stands: still rendering, drawn, or impossible to draw. */
private sealed interface PageImage {
    data object Loading : PageImage
    data class Ready(val bitmap: ImageBitmap) : PageImage
    data object Failed : PageImage
}

@Composable
private fun PdfPage(
    index: Int,
    pageSize: PdfPageSize,
    pageWidthPx: Int,
    zoomedIn: Boolean,
    zoom: () -> Float,
    visibleRegion: Rect?,
    matches: List<PdfSearchMatch>,
    currentMatch: PdfSearchMatch?,
    viewModel: PdfViewerViewModel
) {
    // Paper is white in both themes, so the highlight needs a tone that shows on white in both:
    // primary is the scheme's mid grey either way. A graphite marker rather than a coloured one,
    // in keeping with an app that has no hue of its own.
    val matchColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val currentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val currentOutline = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.pdf_viewer_page_description, index + 1)
    val image by produceState<PageImage>(
        initialValue = viewModel.cachedPageBitmap(index, pageWidthPx)
            ?.let { PageImage.Ready(it.asImageBitmap()) }
            ?: PageImage.Loading,
        index,
        pageWidthPx
    ) {
        value = viewModel.pageBitmap(index, pageWidthPx)
            ?.let { PageImage.Ready(it.asImageBitmap()) }
            ?: PageImage.Failed
    }
    // The on-screen part of the page, re-rendered at the zoomed density. Kept through the next
    // gesture — it is drawn in the page's own coordinates, so it scales and moves with the page —
    // and dropped only once the document is back at 1x.
    val region by produceState<RegionImage?>(null, visibleRegion, zoomedIn) {
        if (visibleRegion == null) {
            if (!zoomedIn) value = null
            return@produceState
        }
        delay(REGION_RENDER_DELAY_MS)
        value = viewModel.regionBitmap(
            index,
            pageWidthPx,
            zoom(),
            visibleRegion.left,
            visibleRegion.top,
            visibleRegion.width,
            visibleRegion.height
        )?.let { RegionImage(it.asImageBitmap(), visibleRegion) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(pageSize.width.toFloat() / pageSize.height)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .drawWithContent {
                drawContent()
                region?.let { sharp ->
                    withTransform({
                        translate(sharp.area.left, sharp.area.top)
                        scale(
                            sharp.area.width / sharp.image.width,
                            sharp.area.height / sharp.image.height,
                            pivot = Offset.Zero
                        )
                    }) {
                        drawImage(sharp.image)
                    }
                }
                if (matches.isNotEmpty()) {
                    val pxPerPt = size.width / pageSize.width
                    val outline = Stroke(width = 1.5.dp.toPx() / zoom())
                    matches.forEach { match ->
                        val isCurrent = match == currentMatch
                        match.rects.forEach { rect ->
                            val topLeft = Offset(rect.left * pxPerPt, rect.top * pxPerPt)
                            val area = Size((rect.right - rect.left) * pxPerPt, (rect.bottom - rect.top) * pxPerPt)
                            drawRect(color = if (isCurrent) currentColor else matchColor, topLeft = topLeft, size = area)
                            if (isCurrent) {
                                drawRect(color = currentOutline, topLeft = topLeft, size = area, style = outline)
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (val current = image) {
            is PageImage.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            is PageImage.Ready -> Image(
                bitmap = current.bitmap,
                contentDescription = description,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            is PageImage.Failed -> PdfMessage(
                icon = Icons.Outlined.BrokenImage,
                text = stringResource(R.string.pdf_viewer_page_error),
                modifier = Modifier.semantics { contentDescription = description }
            )
        }
    }
}

/**
 * The page the reader is on, over the bottom of the document. Tapping it asks for a page to jump to.
 */
@Composable
private fun PageIndicator(
    listState: LazyListState,
    zoom: PdfZoomState,
    pageCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPage by remember(listState, zoom) {
        derivedStateOf { currentPage(listState, zoom) + 1 }
    }
    val goToPage = stringResource(R.string.pdf_viewer_go_to_page)
    // The chip floats over white paper in either theme, so it is always the darker of the app's
    // surface and on-surface tones — a light chip would vanish into the page in dark mode. The
    // inverse roles are not used: the app's schemes leave them at Material's tinted defaults.
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceIsDarker = surface.luminance() < onSurface.luminance()
    val chipColor = if (surfaceIsDarker) surface else onSurface
    val chipContentColor = if (surfaceIsDarker) onSurface else surface
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClickLabel = goToPage, onClick = onClick),
        shape = RoundedCornerShape(50),
        color = chipColor,
        contentColor = chipContentColor,
        shadowElevation = 2.dp
    ) {
        Text(
            text = stringResource(R.string.pdf_viewer_page_indicator, currentPage, pageCount),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/** The page under the middle of the screen, which is the one being read. */
private fun currentPage(listState: LazyListState, zoom: PdfZoomState): Int {
    val info = listState.layoutInfo
    val screenMiddle = Offset(zoom.viewport.width / 2f, zoom.viewport.height / 2f)
    val middle = zoom.toLocal(screenMiddle).y.roundToInt() + info.viewportStartOffset
    return info.visibleItemsInfo.firstOrNull { item ->
        middle >= item.offset && middle < item.offset + item.size + info.mainAxisItemSpacing
    }?.index ?: listState.firstVisibleItemIndex
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfSearchBar(
    search: PdfSearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        TopAppBar(
            title = {
                TextField(
                    value = search.query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.pdf_viewer_search),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            onSearch()
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(x = (-12).dp)
                        .focusRequester(focusRequester)
                )
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.pdf_viewer_search_close)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        if (search.searchedQuery != null) {
            SearchStatusRow(search = search, onPrevious = onPrevious, onNext = onNext)
        }
    }
}

/** Where the reader is among the matches, and the way to the next one. Shown once a search has run. */
@Composable
private fun SearchStatusRow(
    search: PdfSearchState,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val hasMatches = search.matches.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when {
                hasMatches -> pluralStringResource(
                    R.plurals.pdf_viewer_search_position,
                    search.matches.size,
                    search.currentIndex + 1,
                    search.matches.size
                )
                search.inProgress -> ""
                else -> stringResource(R.string.pdf_viewer_search_no_matches)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (search.inProgress) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                strokeWidth = 2.dp
            )
        }
        IconButton(onClick = onPrevious, enabled = hasMatches) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowUp,
                contentDescription = stringResource(R.string.pdf_viewer_search_previous)
            )
        }
        IconButton(onClick = onNext, enabled = hasMatches) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = stringResource(R.string.pdf_viewer_search_next)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoToPageDialog(
    pageCount: Int,
    onDismiss: () -> Unit,
    onGo: (page: Int) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val page = PdfViewerSupport.parsePageNumber(input, pageCount)
    val showError = input.isNotBlank() && page == null

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.pdf_viewer_go_to_page),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.pdf_viewer_go_to_page_hint, pageCount)) },
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(R.string.pdf_viewer_go_to_page_invalid, pageCount)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { page?.let(onGo) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                    TextButton(
                        onClick = { page?.let(onGo) },
                        enabled = page != null,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onBackground
                        )
                    ) {
                        Text(stringResource(R.string.pdf_viewer_go))
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfMessage(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
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

@Composable
private fun PdfViewerActionBar(
    shareEnabled: Boolean,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PdfViewerActionButton(
                icon = Icons.Outlined.Share,
                label = stringResource(R.string.action_share),
                enabled = shareEnabled,
                onClick = onShare
            )
            PdfViewerActionButton(
                icon = Icons.Outlined.Delete,
                label = stringResource(R.string.action_delete),
                enabled = true,
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun PdfViewerActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = tint
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
