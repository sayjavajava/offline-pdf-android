package com.offgridpdf.android.ui.dashboard

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

/**
 * Empty until the first real tool PR (A-3, Split PDF) adds an entry — this
 * scaffolding PR (A-1) intentionally ships no tools, only the shell that
 * later PRs plug into.
 */
val pdfTools: List<PdfTool> = emptyList()
