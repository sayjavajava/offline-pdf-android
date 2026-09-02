package com.offgridpdf.android.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.R
import com.offgridpdf.android.ui.navigation.LocalNavController
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import com.offgridpdf.android.ui.theme.PlexMono

/**
 * Shared chrome for every tool screen (see the UI redesign mockups): a
 * plain top bar with a real back chevron, a bordered file-picker card that
 * shows the chosen filename in mono once picked, a solid primary button
 * tinted by the tool's own category accent, and the one-line privacy
 * reassurance. Pulled out of `ToolScaffold` so `RedactScreen` — which
 * builds its own bespoke layout rather than using `ToolScaffold` — can
 * match the same look without duplicating it.
 */
@Composable
fun ScreenTopBar(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val palette = LocalOffGridPalette.current
    val navController = LocalNavController.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = "Back",
                tint = palette.inkSecondary,
                modifier = Modifier
                    .size(19.dp)
                    .clickable(enabled = navController != null) { navController?.popBackStack() },
            )
            Text(title, style = MaterialTheme.typography.titleLarge, color = palette.ink)
        }
        trailing()
    }
}

@Composable
fun FilePickerCard(fileName: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalOffGridPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.paperRaised)
            .border(BorderStroke(1.dp, palette.hairlineStrong), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_file),
            contentDescription = null,
            tint = if (fileName != null) palette.organize else palette.inkTertiary,
            modifier = Modifier.size(21.dp),
        )
        if (fileName != null) {
            Text(
                fileName,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = PlexMono),
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("Change", style = MaterialTheme.typography.labelMedium, color = palette.organizeLabel)
        } else {
            Text(
                "Choose a file",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkSecondary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalOffGridPalette.current
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) accent else palette.hairlineStrong)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) palette.onAccent else palette.inkTertiary,
        )
    }
}

@Composable
fun PrivacyLine(text: String = "Processed entirely on your device — nothing is uploaded.", modifier: Modifier = Modifier) {
    val palette = LocalOffGridPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkTertiary,
        )
    }
}

@Composable
fun RunningIndicator(accent: Color, modifier: Modifier = Modifier) {
    CircularProgressIndicator(color = accent, modifier = modifier.size(22.dp), strokeWidth = 2.5.dp)
}

