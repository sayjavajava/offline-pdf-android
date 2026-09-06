package com.offgridpdf.android.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.ui.theme.LocalOffGridPalette

/**
 * The form controls every tool screen's options are built from.
 *
 * These exist because the redesign styled the *chrome* — top bar, file
 * picker card, primary button — but left each screen to drop raw Material3
 * widgets into its own options slot. That produced three visible faults,
 * all of which these replace:
 *
 * - **Two kinds of text field on one screen.** `ToolScaffold`'s password
 *   field set palette colors; the 44 fields below it across the app did
 *   not, so they rendered with Material's own container and focus colors
 *   directly beneath a field that didn't. A later pass also dropped
 *   Material's outlined-with-notched-label field entirely: that cutout
 *   does not follow a custom corner radius or these typefaces, so a
 *   filled "Start at" sat next to a custom file-picker card of a
 *   different height with a gap in its border. Fields are now the same
 *   bordered box the dashboard search uses.
 * - **Option rows running off the screen.** A plain `Row` of Material
 *   buttons does not wrap, and several were far wider than a phone: six
 *   page-number positions need roughly 900dp against a 316dp content
 *   width, so four of them simply could not be reached. Same for the
 *   third page-number format, "Not allowed" under Protect's print
 *   permissions, and — worst — Crop/Resize's "Custom" paper size, which
 *   put its whole custom-dimensions feature off the edge of the display.
 * - **Labels floating above their checkbox.** `Row { Checkbox(); Text() }`
 *   with no `verticalAlignment` top-aligns the label against a 48dp-tall
 *   checkbox, and leaves the label itself not clickable.
 */

/**
 * A text field in the app's own style: label above, value in the same
 * bordered `paperRaised` card as [FilePickerCard] and the dashboard
 * search. Not Material's outlined field — that notches the border around
 * a floating label, and the notch does not line up with these corners
 * or fonts once the field has a value.
 */
@Composable
fun ToolTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    val palette = LocalOffGridPalette.current
    var focused by remember { mutableStateOf(false) }
    val border = when {
        !enabled -> palette.hairline
        focused -> accent
        else -> palette.hairlineStrong
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                !enabled -> palette.inkTertiary
                focused -> accent
                else -> palette.inkSecondary
            },
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = if (enabled) palette.ink else palette.inkTertiary,
            ),
            cursorBrush = SolidColor(accent),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.paperRaised)
                        .border(BorderStroke(1.dp, border), RoundedCornerShape(10.dp))
                        // Same inset as FilePickerCard so a password field
                        // stacked under "Choose a file" shares an edge and
                        // a height, instead of Material's 56dp min sitting
                        // next to a ~47dp card.
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.inkTertiary,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/**
 * The heading above a group of options ("Format", "Position", "Colour").
 * Quiet and small on purpose: it labels the control below it rather than
 * competing with the tool's own title.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalOffGridPalette.current.inkSecondary,
        modifier = modifier,
    )
}

/**
 * Explanatory copy inside an options slot — what a tool does, or what a
 * setting will and won't do. Several screens carry a paragraph of this and
 * each was rendering it at a different size.
 */
@Composable
fun ToolBodyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalOffGridPalette.current.inkSecondary,
        modifier = modifier,
    )
}

/**
 * A row of mutually exclusive [OptionChip]s that **wraps** onto as many
 * lines as it needs.
 *
 * `FlowRow` rather than `Row` is the whole point: see this file's header
 * for the options that were unreachable off the right edge without it.
 */
@Composable
fun OptionChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        content()
    }
}

/**
 * One selectable option. Selected fills with the tool's own [accent] —
 * Material's `Button` filled with the theme's `primary` instead, so a
 * selected chip on, say, Protect (a security-accent screen) came out in
 * the organize category's green.
 */
@Composable
fun OptionChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalOffGridPalette.current
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) palette.onAccent else palette.ink,
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) accent else palette.paperRaised)
            .border(
                BorderStroke(1.dp, if (selected) accent else palette.hairlineStrong),
                RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onClick)
            // 12dp vertical against a 12sp label gives a ~40dp tall chip --
            // short of the 48dp ideal, but these sit in groups of up to six
            // and the row would otherwise dominate the screen.
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/**
 * A secondary action — "Preview the crop", "Previous"/"Next", "Choose a
 * different file". Outlined in the tool's own [accent] rather than
 * Material's `primary`, which is the organize category's green on every
 * screen regardless of which tool is open.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalOffGridPalette.current
    val tint = if (enabled) accent else palette.inkTertiary
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(BorderStroke(1.dp, if (enabled) tint else palette.hairlineStrong), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

/** [CheckboxRow]'s single-choice twin, for one option of a radio group. */
@Composable
fun RadioRow(
    selected: Boolean,
    onSelect: () -> Unit,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalOffGridPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onSelect),
    ) {
        CompactChoiceControl {
            RadioButton(
                selected = selected,
                // See CheckboxRow: the row owns the click, so the control must not.
                onClick = null,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = accent,
                    unselectedColor = palette.hairlineStrong,
                ),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) palette.ink else palette.inkTertiary,
            modifier = Modifier.padding(start = 4.dp, end = 8.dp),
        )
    }
}

/**
 * A checkbox and its label, vertically centred on each other, with the
 * whole row clickable rather than just the box.
 */
@Composable
fun CheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalOffGridPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
    ) {
        CompactChoiceControl {
            Checkbox(
                checked = checked,
                enabled = enabled,
                // Null, not a duplicate of the row's own handler: the row
                // carries the click, and letting the box carry it too makes a
                // tap on the box toggle twice back to where it started.
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = accent,
                    checkmarkColor = palette.onAccent,
                    uncheckedColor = palette.hairlineStrong,
                ),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) palette.ink else palette.inkTertiary,
            modifier = Modifier.padding(start = 4.dp, end = 8.dp),
        )
    }
}

/**
 * Material's checkbox/radio is a 20dp glyph inside a 48dp minimum touch
 * target, which insets the glyph ~14dp from the row's left edge — so a
 * [SectionLabel] and a [ToolTextField] share a left edge that the box
 * next to them does not. The row is already the click target, so the
 * extra 48dp is padding we don't need.
 */
@Composable
private fun CompactChoiceControl(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(24.dp),
        ) {
            content()
        }
    }
}
