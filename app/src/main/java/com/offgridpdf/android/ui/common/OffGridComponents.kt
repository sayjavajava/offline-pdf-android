package com.offgridpdf.android.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.R
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.chain.PendingNavigation
import com.offgridpdf.android.chain.ROUTE_DASHBOARD
import com.offgridpdf.android.files.writeBytesToCacheUri
import com.offgridpdf.android.ui.navigation.LocalNavController
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import com.offgridpdf.android.ui.theme.PlexMono
import kotlinx.coroutines.launch

/**
 * Shared chrome for every tool screen (see the UI redesign mockups): a
 * plain top bar with a real back chevron, a bordered file-picker card that
 * shows the chosen filename in mono once picked, a solid primary button
 * tinted by the tool's own category accent, and the one-line privacy
 * reassurance. Pulled out of `ToolScaffold` so `RedactScreen` — which
 * builds its own bespoke layout rather than using `ToolScaffold` — can
 * match the same look without duplicating it.
 */
/**
 * Touch target for a bar's icon button. Android asks for 48dp; 44dp is the
 * compromise Material itself makes for a dense bar, and it is more than
 * twice what these icons had.
 */
private val BACK_TOUCH_TARGET = 44.dp

@Composable
fun ScreenTopBar(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val palette = LocalOffGridPalette.current
    val navController = LocalNavController.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            // The app draws edge to edge (enableEdgeToEdge in MainActivity),
            // which from targetSdk 35 is not optional. Without this the title
            // and the back arrow were painted underneath the status bar, with
            // the clock and battery icon sitting on top of them.
            //
            // The bar takes the inset rather than the screen doing it, so the
            // background still runs to the top edge, and so every screen that
            // uses this bar is fixed by one change. Horizontal is here too for
            // a landscape display cutout; Compose consumes what it applies, so
            // a parent that already handled it costs nothing here.
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
            // Start and vertical padding are smaller than the end padding
            // because the back button's touch target supplies the rest -- see
            // below. The title still lands in the same place.
            .padding(start = 10.dp, end = 22.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // The arrow is drawn at 19dp but hit at 44dp. It used to be both,
            // which put the back button -- on every screen in the app -- at
            // well under half the 48dp minimum touch target, and made it
            // genuinely fiddly to hit. The box carries the click; the icon
            // inside it only draws.
            //
            // The padding above is reduced to match, so this costs 5dp of bar
            // height and leaves the title within a dp of where it was.
            Box(
                modifier = Modifier
                    .size(BACK_TOUCH_TARGET)
                    .clip(CircleShape)
                    .clickable(enabled = navController != null) { navController?.popBackStack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = "Back",
                    tint = palette.inkSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
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

/**
 * Tool chaining's outgoing half: shown once a run has produced [bytes],
 * alongside (not instead of) the normal Save flow. Writes the result to
 * the app's private cache (`files/ChainFile.kt`) and hands it to whichever
 * tool the user picks next via [PendingFile]/[PendingNavigation] — the
 * same mechanism a Share/View intent or a shortcut launch uses to seed a
 * tool screen, so nothing here needs its own bespoke plumbing.
 */
@Composable
fun ContinueChainAction(bytes: ByteArray?, accent: Color, modifier: Modifier = Modifier) {
    if (bytes == null) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(BorderStroke(1.dp, accent), RoundedCornerShape(10.dp))
            .clickable {
                scope.launch {
                    val uri = writeBytesToCacheUri(context, bytes, "chained-result.pdf")
                    PendingFile.set(uri)
                    PendingNavigation.set(ROUTE_DASHBOARD)
                }
            }
            .padding(vertical = 13.dp, horizontal = 14.dp),
    ) {
        Text(
            "Continue with another tool",
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp),
        )
    }
}

