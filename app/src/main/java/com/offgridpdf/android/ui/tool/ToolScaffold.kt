package com.offgridpdf.android.ui.tool

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.offgridpdf.android.files.SavedFile
import com.offgridpdf.android.ui.common.ContinueChainAction
import com.offgridpdf.android.ui.common.FilePickerCard
import com.offgridpdf.android.ui.common.PrimaryButton
import com.offgridpdf.android.ui.common.PrivacyLine
import com.offgridpdf.android.ui.common.RunningIndicator
import com.offgridpdf.android.ui.common.ToolCompletion
import com.offgridpdf.android.ui.common.ToolScreenScaffold
import com.offgridpdf.android.ui.common.ToolTextField
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
 * The frame — paper background, top bar, scrolling capped-width content,
 * pinned bottom block — is [ToolScreenScaffold], shared with the screens
 * whose flow doesn't fit this one.
 *
 * [batchNote]: batch mode's one piece of UI (Compress/Watermark/Rotate/
 * Page Numbers — `files/BatchRun.kt`) — set it once more than one file is
 * picked, to say plainly that every setting below applies to all of them
 * before the user configures anything. Absent for a single file, exactly
 * like every tool screen looked before batch mode existed.
 */
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
    /** Set when the run finished and a file really reached disk. Drives the
     * success styling and the Share action. */
    savedFile: SavedFile? = null,
    chainableBytes: ByteArray? = null,
    /** Forwarded to [ToolCompletion]/[ContinueChainAction] — see their docs. */
    chainOriginBaseName: String = "",
    chainedFileName: String = "",
    batchNote: String? = null,
    options: @Composable () -> Unit = {},
) {
    val palette = LocalOffGridPalette.current

    ToolScreenScaffold(
        title = title,
        bottomBar = {
            if (running) {
                RunningIndicator(accent = accent)
            }
            resultMessage?.let { message ->
                // One banner for the end of a run, success or failure, rather
                // than a line of grey text that read the same either way.
                // ContinueChainAction lives inside it now, which also stops it
                // being offered after a failure -- there was nothing to
                // continue with, and it used to appear anyway.
                ToolCompletion(
                    message = message,
                    savedFile = savedFile,
                    accent = accent,
                    chainableBytes = chainableBytes,
                    chainOriginBaseName = chainOriginBaseName,
                    chainedFileName = chainedFileName,
                )
            }
            PrivacyLine()
            PrimaryButton(
                text = runLabel,
                onClick = onRun,
                accent = accent,
                enabled = runEnabled && !running,
            )
        },
    ) {
        FilePickerCard(fileName = pickedFileName, onClick = onPickFile)

        batchNote?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary)
        }

        ToolTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "Password (if encrypted)",
            accent = accent,
            visualTransformation = PasswordVisualTransformation(),
        )

        options()
    }
}
