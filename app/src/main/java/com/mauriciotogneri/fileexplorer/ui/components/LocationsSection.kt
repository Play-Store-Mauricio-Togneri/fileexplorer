package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.Location

private val SizePlaceholderWidth = 48.dp
private val SizePlaceholderHeight = 10.dp
private val SizePlaceholderShape = RoundedCornerShape(SizePlaceholderHeight / 2)

/** Test tag on the bar a card shows while its location has no size yet. */
const val LOCATION_SIZE_PLACEHOLDER_TEST_TAG = "location_size_placeholder"

@Composable
fun LocationsSection(
    locations: List<Location>,
    onLocationClick: (Location, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (locations.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.section_locations),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        // 2-column grid using rows
        val chunkedLocations = remember(locations) { locations.chunked(2) }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            chunkedLocations.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { location ->
                        val title = stringResource(location.type.titleResId)
                        LocationCard(
                            location = location,
                            onClick = { onLocationClick(location, title) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill empty space if odd number of items
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationCard(
    location: Location,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = location.type.icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(location.type.titleResId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                val formattedSize = location.formattedSize

                if (formattedSize != null) {
                    Text(
                        text = formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SizePlaceholder()
                }
            }
        }
    }
}

// Stands in for the size of a location that has never been measured, until the walk that is
// measuring it finishes. Sized to one line of the text it replaces, so the card does not change
// height when the size arrives.
@Composable
private fun SizePlaceholder() {
    val lineHeight = with(LocalDensity.current) {
        MaterialTheme.typography.bodySmall.lineHeight.toDp()
    }

    Box(
        modifier = Modifier.height(lineHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .testTag(LOCATION_SIZE_PLACEHOLDER_TEST_TAG)
                .size(width = SizePlaceholderWidth, height = SizePlaceholderHeight)
                .background(
                    // outlineVariant, not a surfaceContainer role: the app's schemes leave
                    // surfaceContainerHighest unset, so it would fall back to the baseline's
                    // violet tint and barely clear the card in either theme.
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = SizePlaceholderShape
                )
        )
    }
}
