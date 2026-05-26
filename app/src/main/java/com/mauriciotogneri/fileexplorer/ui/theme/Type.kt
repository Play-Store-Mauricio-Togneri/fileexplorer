package com.mauriciotogneri.fileexplorer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

val MenuItemTextStyle: TextStyle
    @Composable
    get() = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal)

val AppBarTitleStyle: TextStyle
    @Composable
    get() = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp)
