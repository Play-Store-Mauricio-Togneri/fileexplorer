package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.SearchFileType
import com.mauriciotogneri.fileexplorer.data.model.SearchFilters
import com.mauriciotogneri.fileexplorer.data.model.SearchItemKind

private enum class FilterSheet { NONE, KIND, HIDDEN, TYPE }

/**
 * A horizontal row of three summary chips (kind, hidden, type) shown under the search field. Each
 * chip displays its current value and opens a bottom sheet to change it. A chip is "selected"
 * (filled) when its value differs from the neutral default. The type chip is disabled while the
 * kind is Folders, since folders have no file type.
 */
@Composable
fun SearchFiltersBar(
    filters: SearchFilters,
    onItemKindSelected: (SearchItemKind) -> Unit,
    onIncludeHiddenChanged: (Boolean) -> Unit,
    onTypeToggled: (SearchFileType) -> Unit,
    onAllTypesSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    var openSheet by remember { mutableStateOf(FilterSheet.NONE) }

    val typeEnabled = filters.itemKind != SearchItemKind.FOLDERS

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Chips always render in the same outlined (unselected) style; the current value is
        // conveyed by the label and leading icon, so selection is never shown as a filled chip.
        FilterChip(
            selected = false,
            onClick = { openSheet = FilterSheet.KIND },
            label = { Text(kindLabel(filters.itemKind)) },
            leadingIcon = { ChipIcon(kindIcon(filters.itemKind)) },
            trailingIcon = { ChipCaret() }
        )
        FilterChip(
            selected = false,
            onClick = { openSheet = FilterSheet.HIDDEN },
            label = { Text(stringResource(R.string.search_filter_hidden)) },
            leadingIcon = {
                ChipIcon(if (filters.includeHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff)
            },
            trailingIcon = { ChipCaret() }
        )
        FilterChip(
            selected = false,
            enabled = typeEnabled,
            onClick = { openSheet = FilterSheet.TYPE },
            label = { Text(typeChipLabel(filters.selectedTypes)) },
            leadingIcon = { ChipIcon(typeChipIcon(filters.selectedTypes)) },
            trailingIcon = { ChipCaret() }
        )
    }

    when (openSheet) {
        FilterSheet.KIND -> KindFilterSheet(
            selected = filters.itemKind,
            onSelect = {
                onItemKindSelected(it)
                openSheet = FilterSheet.NONE
            },
            onDismiss = { openSheet = FilterSheet.NONE }
        )

        FilterSheet.HIDDEN -> HiddenFilterSheet(
            includeHidden = filters.includeHidden,
            onSelect = {
                onIncludeHiddenChanged(it)
                openSheet = FilterSheet.NONE
            },
            onDismiss = { openSheet = FilterSheet.NONE }
        )

        FilterSheet.TYPE -> TypeFilterSheet(
            selectedTypes = filters.selectedTypes,
            onAllTypesSelected = onAllTypesSelected,
            onTypeToggled = onTypeToggled,
            onDismiss = { openSheet = FilterSheet.NONE }
        )

        FilterSheet.NONE -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindFilterSheet(
    selected: SearchItemKind,
    onSelect: (SearchItemKind) -> Unit,
    onDismiss: () -> Unit
) {
    FilterSheetScaffold(onDismiss = onDismiss) {
        SearchItemKind.entries.forEach { kind ->
            RadioOptionRow(
                icon = kindIcon(kind),
                label = kindLabel(kind),
                selected = kind == selected,
                onClick = { onSelect(kind) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HiddenFilterSheet(
    includeHidden: Boolean,
    onSelect: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    FilterSheetScaffold(onDismiss = onDismiss) {
        RadioOptionRow(
            icon = Icons.Outlined.Visibility,
            label = stringResource(R.string.show_hidden_items),
            selected = includeHidden,
            onClick = { onSelect(true) }
        )
        RadioOptionRow(
            icon = Icons.Outlined.VisibilityOff,
            label = stringResource(R.string.hide_hidden_items),
            selected = !includeHidden,
            onClick = { onSelect(false) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeFilterSheet(
    selectedTypes: Set<SearchFileType>,
    onAllTypesSelected: () -> Unit,
    onTypeToggled: (SearchFileType) -> Unit,
    onDismiss: () -> Unit
) {
    FilterSheetScaffold(onDismiss = onDismiss) {
        CheckboxOptionRow(
            icon = Icons.Outlined.SelectAll,
            label = stringResource(R.string.search_filter_type_all),
            checked = selectedTypes.isEmpty(),
            onClick = onAllTypesSelected
        )
        SearchFileType.entries.forEach { type ->
            CheckboxOptionRow(
                icon = typeIcon(type),
                label = typeLabel(type),
                checked = type in selectedTypes,
                onClick = { onTypeToggled(type) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheetScaffold(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { FullWidthDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun RadioOptionRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OptionRow(
        icon = icon,
        label = label,
        modifier = Modifier.selectable(
            selected = selected,
            onClick = onClick,
            role = Role.RadioButton
        )
    ) {
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun CheckboxOptionRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    OptionRow(
        icon = icon,
        label = label,
        modifier = Modifier.toggleable(
            value = checked,
            onValueChange = { onClick() },
            role = Role.Checkbox
        )
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(20.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        trailing()
    }
}

@Composable
private fun ChipIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(FilterChipDefaults.IconSize)
    )
}

@Composable
private fun ChipCaret() {
    Icon(
        imageVector = Icons.Outlined.ArrowDropDown,
        contentDescription = null,
        modifier = Modifier.size(FilterChipDefaults.IconSize)
    )
}

private fun kindIcon(kind: SearchItemKind): ImageVector = when (kind) {
    SearchItemKind.FILES -> Icons.AutoMirrored.Outlined.InsertDriveFile
    SearchItemKind.FOLDERS -> Icons.Outlined.Folder
    SearchItemKind.ANY -> Icons.Outlined.SelectAll
}

@Composable
private fun kindLabel(kind: SearchItemKind): String = stringResource(
    when (kind) {
        SearchItemKind.FILES -> R.string.search_filter_kind_files
        SearchItemKind.FOLDERS -> R.string.search_filter_kind_folders
        SearchItemKind.ANY -> R.string.search_filter_kind_any
    }
)

private fun typeIcon(type: SearchFileType): ImageVector = when (type) {
    SearchFileType.IMAGES -> Icons.Outlined.Image
    SearchFileType.AUDIO -> Icons.Outlined.MusicNote
    SearchFileType.VIDEOS -> Icons.Outlined.PlayCircle
    SearchFileType.DOCUMENTS -> Icons.Outlined.Description
    SearchFileType.OTHER -> Icons.Outlined.Category
}

@Composable
private fun typeLabel(type: SearchFileType): String = stringResource(
    when (type) {
        SearchFileType.IMAGES -> R.string.location_images
        SearchFileType.AUDIO -> R.string.location_audio
        SearchFileType.VIDEOS -> R.string.location_videos
        SearchFileType.DOCUMENTS -> R.string.location_documents
        SearchFileType.OTHER -> R.string.search_filter_type_other
    }
)

private fun typeChipIcon(selectedTypes: Set<SearchFileType>): ImageVector {
    val first = SearchFileType.entries.firstOrNull { it in selectedTypes }
    return if (first == null) Icons.Outlined.SelectAll else typeIcon(first)
}

@Composable
private fun typeChipLabel(selectedTypes: Set<SearchFileType>): String {
    val ordered = SearchFileType.entries.filter { it in selectedTypes }
    return when (ordered.size) {
        0 -> stringResource(R.string.search_filter_type_all)
        1 -> typeLabel(ordered.first())
        else -> stringResource(
            R.string.search_filter_type_summary,
            typeLabel(ordered.first()),
            ordered.size - 1
        )
    }
}
