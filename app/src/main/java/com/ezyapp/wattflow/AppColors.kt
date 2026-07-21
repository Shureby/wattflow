package com.ezyapp.wattflow

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Semantic tag colors not covered by Material3's default primary/error
 * roles -- used first by Health Trend's per-reading tags, written so
 * other screens (Ledger, History) can adopt the same palette later
 * instead of each picking their own.
 */
object AppColors {
    val success: Color
        @Composable @ReadOnlyComposable get() =
            if (isSystemInDarkTheme()) Color(0xFF7FD99C) else Color(0xFF2F7A4F)
    val successContainer: Color
        @Composable @ReadOnlyComposable get() =
            if (isSystemInDarkTheme()) Color(0xFF1D3327) else Color(0xFFE3F3E8)

    val info: Color
        @Composable @ReadOnlyComposable get() =
            if (isSystemInDarkTheme()) Color(0xFF8FA8E8) else Color(0xFF3F5FB0)
    val infoContainer: Color
        @Composable @ReadOnlyComposable get() =
            if (isSystemInDarkTheme()) Color(0xFF202A42) else Color(0xFFEEF2FB)

    val warning: Color
        @Composable @ReadOnlyComposable get() =
            if (isSystemInDarkTheme()) Color(0xFFE0B876) else Color(0xFF9A6A1C)
    val warningContainer: Color
        @Composable @ReadOnlyComposable get() =
            if (isSystemInDarkTheme()) Color(0xFF3A2E17) else Color(0xFFFDF1DE)
}
