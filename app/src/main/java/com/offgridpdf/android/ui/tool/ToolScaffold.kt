package com.offgridpdf.android.ui.tool

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
 */
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
    options: @Composable () -> Unit = {},
) {
    val palette = LocalOffGridPalette.current

    Column(modifier = Modifier.fillMaxSize().background(palette.paper)) {
        ScreenTopBar(title = title)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            FilePickerCard(fileName = pickedFileName, onClick = onPickFile)

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
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (running) {
                RunningIndicator(accent = accent)
            }
            resultMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary)
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
