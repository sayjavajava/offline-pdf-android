package com.offgridpdf.android.ui.tool

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.ui.common.ContinueChainAction
import com.offgridpdf.android.ui.common.FilePickerCard
import com.offgridpdf.android.ui.common.PrimaryButton
import com.offgridpdf.android.ui.common.PrivacyLine
import com.offgridpdf.android.ui.common.RunningIndicator
import com.offgridpdf.android.ui.common.ScreenTopBar
import com.offgridpdf.android.ui.theme.LocalOffGridPalette

/**
 * The shape every tool screen shares (`ANDROID_IMPLEMENTATION_PLAN.md` A-2,
 * tool-docs repo): pick a file, optionally supply a password, configure
 * whatever this specific tool needs via [options], run it, see progress,
 * see the result. Restyled to the "paper & ink" redesign (see the UI
 * redesign mockups) — same shape, new chrome: a plain top bar with a real
 * back chevron, a bordered file-picker card, and a primary button tinted by
 * [accent] (the tool's own category color), pinned below scrollable content
 * so it stays reachable regardless of how much [options] adds.
 *
 * The password field is always shown, labeled "(if encrypted)", never
 * conditionally revealed after a failed load — corrected here in A-3 after
 * checking the real precedent (`SplitTool.tsx`, `MergeTool.tsx`: both show
 * a plain, always-present password field, not a reactive one).
 *
 * [chainableBytes]: the tool's own result bytes, once a run has produced
 * one — pass whatever was last written via the save launcher, kept around
 * rather than cleared afterward. When non-null and [resultMessage] is set,
 * shows "Continue with another tool" (`ui/common/OffGridComponents.kt`)
 * alongside the normal save flow, not instead of it.
 *
 * Content is capped at [MAX_CONTENT_WIDTH] and centered — a no-op on a
 * phone (always narrower), but keeps text fields and buttons from
 * stretching edge to edge in the wide detail pane `OffGridNavHost.kt`
 * gives this screen on a tablet/expanded-width window.
 *
 * [batchNote]: batch mode's one piece of UI (Compress/Watermark/Rotate/
 * Page Numbers — `files/BatchRun.kt`) — set it once more than one file is
 * picked, to say plainly that every setting below applies to all of them
 * before the user configures anything. Absent for a single file, exactly
 * like every tool screen looked before batch mode existed.
 */
private val MAX_CONTENT_WIDTH = 560.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(
    title: String,
    accent: Color,
    pickedFileName: String?,
    onPickFile: () -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    runEnabled: Boolean,
    running: Boolean,
    onRun: () -> Unit,
    runLabel: String = "Run",
    resultMessage: String?,
    chainableBytes: ByteArray? = null,
    batchNote: String? = null,
    options: @Composable () -> Unit = {},
) {
    val palette = LocalOffGridPalette.current

    Column(
        modifier = Modifier
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
        ScreenTopBar(title = title)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .widthIn(max = MAX_CONTENT_WIDTH)
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            FilePickerCard(fileName = pickedFileName, onClick = onPickFile)

            batchNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary)
            }

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password (if encrypted)") },
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = palette.hairlineStrong,
                    focusedContainerColor = palette.paperRaised,
                    unfocusedContainerColor = palette.paperRaised,
                    focusedTextColor = palette.ink,
                    unfocusedTextColor = palette.ink,
                    focusedLabelColor = accent,
                    unfocusedLabelColor = palette.inkTertiary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            options()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MAX_CONTENT_WIDTH)
                // The run button lives here, at the bottom of an edge-to-edge
                // window, so without this the gesture pill sat on top of it.
                // safeDrawing rather than navigationBars so it also lifts clear
                // of the keyboard: every one of these screens has a password
                // field, and the button was unreachable behind the IME.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (running) {
                RunningIndicator(accent = accent)
            }
            resultMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary)
            }
            if (resultMessage != null) {
                ContinueChainAction(bytes = chainableBytes, accent = accent)
            }
            PrivacyLine()
            PrimaryButton(
                text = runLabel,
                onClick = onRun,
                accent = accent,
                enabled = runEnabled && !running,
            )
        }
    }
}
