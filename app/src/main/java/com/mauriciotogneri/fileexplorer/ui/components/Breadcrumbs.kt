package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class BreadcrumbItem(
    val name: String,
    val path: String
)

@Composable
fun Breadcrumbs(
    currentPath: String,
    onNavigateToPath: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(currentPath) {
        parsePath(currentPath)
    }

    val listState = rememberLazyListState()

    // Scroll to the end when items change (prioritize right side)
    LaunchedEffect(items) {
        if (items.isNotEmpty()) {
            listState.scrollToItem(items.lastIndex)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.path }
            ) { index, item ->
                BreadcrumbSegment(
                    item = item,
                    isLast = index == items.lastIndex,
                    onClick = { onNavigateToPath(item.path) }
                )
            }
        }
    }
}

@Composable
private fun BreadcrumbSegment(
    item: BreadcrumbItem,
    isLast: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isLast) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clickable(enabled = !isLast, onClick = onClick)
                .padding(vertical = 4.dp, horizontal = 4.dp)
        )

        if (!isLast) {
            Text(
                text = "/",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

private fun parsePath(path: String): List<BreadcrumbItem> {
    if (path.isBlank()) return emptyList()

    val segments = path.trimStart('/').split('/').filter { it.isNotEmpty() }
    val items = mutableListOf<BreadcrumbItem>()

    var currentPath = ""
    for (segment in segments) {
        currentPath = "$currentPath/$segment"
        items.add(
            BreadcrumbItem(
                name = segment,
                path = currentPath
            )
        )
    }

    return items
}
