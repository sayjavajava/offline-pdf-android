package com.offgridpdf.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.R
import com.offgridpdf.android.ui.common.ScreenTopBar
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import com.offgridpdf.android.ui.theme.ThemeMode
import com.offgridpdf.android.ui.theme.ThemeState

/**
 * The app's one setting so far: light/dark theme. `System` (the default)
 * follows the device; `Light`/`Dark` override it. Selecting a row applies
 * immediately via [ThemeState] — [MaterialTheme] wraps this whole screen
 * too, so the change is visible here, live, not just on screens visited
 * afterward.
 */
@Composable
fun SettingsScreen() {
    val palette = LocalOffGridPalette.current
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(palette.paper)) {
        ScreenTopBar(title = "Settings")
        LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp)) {
            item {
                Text(
                    "APPEARANCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkTertiary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 10.dp),
                )
                HorizontalDivider(color = palette.hairline, thickness = 1.dp)
            }
            items(ThemeMode.entries) { mode ->
                ThemeRow(
                    mode = mode,
                    selected = ThemeState.mode == mode,
                    onClick = { ThemeState.update(context, mode) },
                )
                HorizontalDivider(color = palette.hairline, thickness = 1.dp)
            }
        }
    }
}

private fun descriptionFor(mode: ThemeMode): String = when (mode) {
    ThemeMode.System -> "Match your device's setting"
    ThemeMode.Light -> "Always use the light theme"
    ThemeMode.Dark -> "Always use the dark theme"
}

@Composable
private fun ThemeRow(mode: ThemeMode, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalOffGridPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 2.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(mode.label, style = MaterialTheme.typography.titleMedium, color = palette.ink)
            Text(descriptionFor(mode), style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary)
        }
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = "Selected",
                tint = palette.organize,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
