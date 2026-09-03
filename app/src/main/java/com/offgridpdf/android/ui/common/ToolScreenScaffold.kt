package com.offgridpdf.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.ui.theme.LocalOffGridPalette

/** Wide enough for a form, narrow enough to stay readable — see [ToolScreenScaffold]. */
val TOOL_CONTENT_MAX_WIDTH = 560.dp

/**
 * The frame every tool screen sits in, whatever its own shape.
 *
 * `ToolScaffold` is the common case — pick a file, set a password, press
 * Run — and it is built on this. The half-dozen screens whose flow doesn't
 * fit that (Merge takes many files and no password; Fill Form and Compare
 * have their own multi-step shapes; Signature draws a canvas) each used to
 * hand-roll their own `Scaffold`, and each got it a bit wrong: 16dp padding
 * against the 22dp everything else uses, no `Arrangement.spacedBy` at all
 * so controls sat flush against each other, no width cap on a tablet, and —
 * on four of them — **no scrolling**, so a long file list or a form with
 * more than a screenful of fields pushed the action button somewhere it
 * could not be reached.
 *
 * Sharing the frame is what keeps those from drifting apart again:
 *
 * - `paper` background reaching the screen edge, with the horizontal
 *   display-cutout inset applied once, above.
 * - [ScreenTopBar], which takes the status-bar inset itself.
 * - A scrolling content column, capped at [TOOL_CONTENT_MAX_WIDTH] and
 *   centred, 22dp side padding, 18dp between children.
 * - A pinned [bottomBar] that clears the navigation bar *and* the keyboard,
 *   so an action button is never behind either.
 */
@Composable
fun ToolScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    topBarTrailing: @Composable () -> Unit = {},
    bottomBar: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalOffGridPalette.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.paper)
            // Horizontal once, here, for a landscape display cutout. Applied
            // after background so the paper still reaches the screen edge.
            // Compose consumes the insets a modifier applies, so ScreenTopBar
            // and the bottom block below see horizontal already handled and
            // only add their own top and bottom.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenTopBar(title = title, trailing = topBarTrailing)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .widthIn(max = TOOL_CONTENT_MAX_WIDTH)
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = TOOL_CONTENT_MAX_WIDTH)
                // The action button lives here, at the bottom of an
                // edge-to-edge window, so without this the gesture pill sat on
                // top of it. safeDrawing rather than navigationBars so it also
                // lifts clear of the keyboard.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = bottomBar,
        )
    }
}
