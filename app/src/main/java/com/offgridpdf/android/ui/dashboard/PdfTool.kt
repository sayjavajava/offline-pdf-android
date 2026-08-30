package com.offgridpdf.android.ui.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Same 4-category grouping as the web app's `GlassDashboard.tsx` /
 * `pdfTools` array — keep this list and that one in sync by hand; there is
 * no shared source of truth between the two codebases (they're a rewrite,
 * not a shared package). See `ANDROID_IMPLEMENTATION_PLAN.md` (tool-docs
 * repo) for the full 19-tool list and the order they're implemented in.
 */
enum class ToolCategory(val label: String) {
    OrganizePages("Organize Pages"),
    Security("Security"),
    ConvertExport("Convert & Export"),
    EditEnhance("Edit & Enhance"),
}

data class PdfTool(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val category: ToolCategory,
)

val pdfTools: List<PdfTool> = listOf(
    PdfTool(
        id = "split",
        title = "Split PDF",
        description = "Extract pages or divide documents with surgical precision.",
        icon = Icons.Filled.List,
        category = ToolCategory.OrganizePages,
    ),
)
