package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.mauriciotogneri.fileexplorer.ui.theme.MenuItemTextStyle

/**
 * One action in a file actions sheet. [isDestructive] tints it `error`, as the swipe-to-delete
 * button and the delete dialog already are; every sheet places such an action last.
 */
@Composable
fun FileActionsSheetItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val color = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MenuItemTextStyle,
                color = if (isDestructive) color else Color.Unspecified
            )
        },
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = color
            )
        }
    )
}
