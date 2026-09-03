package com.offgridpdf.android.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.R
import com.offgridpdf.android.chain.PendingFile
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import com.offgridpdf.android.ui.theme.PlexMono

/**
 * The reworked dashboard (see the UI redesign mockups): an editorial,
 * hairline-divided tool list instead of a category-tab grid — with a
 * functional search filter and a Recent row up top so reaching a tool
 * never depends on scrolling through all 20. Web reference for the tool
 * data itself only (`GlassDashboard.tsx` / `pdfTools`), not this screen's
 * layout — this is a from-scratch native shape, not a port of the tab+grid
 * one it replaces.
 */
@Composable
fun DashboardScreen(
    recentToolsStore: RecentToolsStore,
    onToolSelected: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
) {
    val palette = LocalOffGridPalette.current
    var query by remember { mutableStateOf("") }
    val pendingUri = PendingFile.uri

    val recentTools = remember(recentToolsStore) {
        recentToolsStore.recentToolIds().mapNotNull { id -> pdfTools.find { it.id == id } }
    }.take(4)

    val normalizedQuery = query.trim()
    val groupedTools = ToolCategory.entries.associateWith { category ->
        pdfTools.filter { tool ->
            tool.category == category &&
                (
                    normalizedQuery.isBlank() ||
                        tool.title.contains(normalizedQuery, ignoreCase = true) ||
                        tool.description.contains(normalizedQuery, ignoreCase = true)
                    )
        }
    }

    // Insets go in contentPadding, not on the LazyColumn itself, so the list
    // scrolls *under* the status and navigation bars while the first and last
    // rows still clear them. Padding the container instead would leave a dead
    // band of background at both ends. Before this, the masthead sat behind
    // the clock and the last tool row behind the gesture pill.
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(palette.paper),
        contentPadding = PaddingValues(
            start = 22.dp + safeInsets.calculateStartPadding(layoutDirection),
            end = 22.dp + safeInsets.calculateEndPadding(layoutDirection),
            top = 28.dp + safeInsets.calculateTopPadding(),
            bottom = 28.dp + safeInsets.calculateBottomPadding(),
        ),
    ) {
        item {
            Masthead(onSettingsClick = onSettingsClick)
            Spacer(Modifier.height(20.dp))
            SearchField(query = query, onQueryChange = { query = it })
        }

        if (pendingUri != null) {
            item {
                Spacer(Modifier.height(16.dp))
                PendingFileBanner(fileName = pendingUri.lastPathSegment, onDismiss = { PendingFile.clear() })
            }
        }

        if (normalizedQuery.isBlank() && recentTools.isNotEmpty()) {
            item {
                Spacer(Modifier.height(28.dp))
                Text(
                    "RECENT",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkTertiary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 10.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(recentTools, key = { it.id }) { tool ->
                        RecentChip(tool = tool, onClick = { onToolSelected(tool.id) })
                    }
                }
            }
        }

        ToolCategory.entries.forEach { category ->
            val tools = groupedTools[category].orEmpty()
            if (tools.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(30.dp))
                    Text(
                        category.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = category.labelAccent(palette),
                        modifier = Modifier.padding(start = 2.dp, bottom = 10.dp),
                    )
                    HorizontalDivider(color = palette.hairline, thickness = 1.dp)
                }
                tools.forEach { tool ->
                    item(key = tool.id) {
                        ToolRow(tool = tool, accent = category.accent(palette), onClick = { onToolSelected(tool.id) })
                        HorizontalDivider(color = palette.hairline, thickness = 1.dp)
                    }
                }
            }
        }

        if (normalizedQuery.isNotBlank() && groupedTools.values.all { it.isEmpty() }) {
            item {
                Spacer(Modifier.height(40.dp))
                Text(
                    "No tools match “$query”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkSecondary,
                )
            }
        }
    }
}

@Composable
private fun Masthead(onSettingsClick: () -> Unit) {
    val palette = LocalOffGridPalette.current
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_brand_mark),
                contentDescription = null,
                tint = palette.organize,
                modifier = Modifier.size(30.dp),
            )
            Column {
                Text(
                    "OffGridPDF",
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Every tool runs on this device. Nothing you open ever leaves it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkTertiary,
                )
            }
        }
        // Drawn at 20dp, hit at 44dp. It used to be both, which is under
        // half the 48dp minimum touch target -- and this is the only way
        // into Settings.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = "Settings",
                tint = palette.inkSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val palette = LocalOffGridPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(palette.paperRaised)
            .border(BorderStroke(1.dp, palette.hairlineStrong), RoundedCornerShape(9.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = palette.inkTertiary,
            modifier = Modifier.size(16.dp),
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            if (query.isEmpty()) {
                Text(
                    "Search ${pdfTools.size} tools…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkTertiary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.ink),
                cursorBrush = SolidColor(palette.organize),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RecentChip(tool: PdfTool, onClick: () -> Unit) {
    val palette = LocalOffGridPalette.current
    val accent = tool.category.accent(palette)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.paperRaised)
            .border(BorderStroke(1.dp, palette.hairlineStrong), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
    ) {
        Icon(
            painter = painterResource(tool.icon),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            tool.title,
            style = MaterialTheme.typography.titleSmall,
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ToolRow(tool: PdfTool, accent: Color, onClick: () -> Unit) {
    val palette = LocalOffGridPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 2.dp),
    ) {
        Icon(
            painter = painterResource(tool.icon),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(23.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(tool.title, style = MaterialTheme.typography.titleMedium, color = palette.ink)
            Spacer(Modifier.height(2.dp))
            Text(
                tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = palette.inkTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Shown when [PendingFile] carries a file — either shared/opened into the
 * app from elsewhere, or handed off by "Continue with another tool"
 * (`ToolScaffold.kt`, `RedactScreen.kt`). Names it and lets the user pick
 * any tool row below to run on it; dismissing just drops the pending file,
 * it doesn't undo whatever produced it.
 */
@Composable
private fun PendingFileBanner(fileName: String?, onDismiss: () -> Unit) {
    val palette = LocalOffGridPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.paperRaised)
            .border(BorderStroke(1.dp, palette.organize), RoundedCornerShape(10.dp))
            .padding(start = 14.dp, end = 0.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_file),
            contentDescription = null,
            tint = palette.organize,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Continue with this file",
                style = MaterialTheme.typography.titleSmall,
                color = palette.ink,
            )
            Text(
                fileName ?: "Choose a tool below",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlexMono),
                color = palette.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Drawn at 16dp, hit at 44dp -- it was the smallest target in the
        // app, and a dismiss control that is hard to hit is one people end
        // up jabbing at repeatedly. The banner's end padding is reduced to
        // absorb the extra width.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Dismiss",
                tint = palette.inkTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
