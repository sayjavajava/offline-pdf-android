package com.offgridpdf.android.ui.tool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * The shape every tool screen shares (`ANDROID_IMPLEMENTATION_PLAN.md` A-2,
 * tool-docs repo): pick a file, optionally supply a password, configure
 * whatever this specific tool needs via [options], run it, see progress,
 * see the result. Mirrors the common shape every `*Tool.tsx` component in
 * the web app already follows.
 *
 * The password field is always shown, labeled "(if encrypted)", never
 * conditionally revealed after a failed load — corrected here in A-3 after
 * checking the real precedent (`SplitTool.tsx`, `MergeTool.tsx`: both show
 * a plain, always-present password field, not a reactive one). A-2's first
 * draft guessed a conditional field before a second real tool existed to
 * check that guess against — exactly the "expand/correct once real needs
 * are obvious" case its own header comment anticipated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(
    title: String,
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
    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
                Text(pickedFileName ?: "Choose a PDF")
            }

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password (if encrypted)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            options()

            Button(
                onClick = onRun,
                enabled = runEnabled && !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(runLabel)
            }

            if (running) {
                CircularProgressIndicator()
            }

            resultMessage?.let { Text(it) }
        }
    }
}
