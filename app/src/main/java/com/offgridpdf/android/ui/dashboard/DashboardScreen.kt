package com.offgridpdf.android.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Mirrors `GlassDashboard.tsx`'s own shape: a category tab row, then a grid
 * of the selected category's tools. Ported behavior, not markup — this
 * screen owns its own layout, not a translation of the web component's JSX.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onToolSelected: (String) -> Unit) {
    var selectedCategory by remember { mutableStateOf(ToolCategory.OrganizePages) }
    val visibleTools = pdfTools.filter { it.category == selectedCategory }

    Scaffold(
        topBar = { TopAppBar(title = { Text("OffGridPDF") }) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ScrollableTabRow(selectedTabIndex = selectedCategory.ordinal) {
                ToolCategory.entries.forEach { category ->
                    val count = pdfTools.count { it.category == category }
                    Tab(
                        selected = category == selectedCategory,
                        onClick = { selectedCategory = category },
                        text = { Text("${category.label} ($count)") },
                    )
                }
            }

            if (visibleTools.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tools in this category yet.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visibleTools, key = { it.id }) { tool ->
                        ToolCard(tool = tool, onClick = { onToolSelected(tool.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(tool: PdfTool, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(imageVector = tool.icon, contentDescription = null)
            Text(tool.title)
            Text(tool.description)
        }
    }
}
