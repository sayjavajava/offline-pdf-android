package com.offgridpdf.android.ui.tool

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Stand-in destination until the first real tool screen exists (A-3, Split
 * PDF). Each subsequent tool PR replaces its own `toolId`'s route with a
 * real screen — this scaffold just proves navigation-by-tool-id works.
 */
@Composable
fun ToolPlaceholderScreen(toolId: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Tool \"$toolId\" is not implemented yet.")
    }
}
