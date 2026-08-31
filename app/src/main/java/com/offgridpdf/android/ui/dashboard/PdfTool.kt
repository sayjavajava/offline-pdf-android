package com.offgridpdf.android.ui.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Refresh
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
    PdfTool(
        id = "merge",
        title = "Merge PDF",
        description = "Combine multiple PDF documents into a single file.",
        icon = Icons.Filled.MergeType,
        category = ToolCategory.OrganizePages,
    ),
    PdfTool(
        id = "rotate",
        title = "Rotate Pages",
        description = "Rotate selected pages by 90°, 180°, or 270°.",
        icon = Icons.Filled.Refresh,
        category = ToolCategory.OrganizePages,
    ),
    PdfTool(
        id = "rearrange",
        title = "Delete / Reorder",
        description = "Keep pages in a custom order; omit pages to delete them.",
        icon = Icons.Filled.Reorder,
        category = ToolCategory.OrganizePages,
    ),
    PdfTool(
        id = "crop-resize",
        title = "Crop / Resize Pages",
        description = "Trim margins non-destructively, or rescale pages to a target size.",
        icon = Icons.Filled.Crop,
        category = ToolCategory.OrganizePages,
    ),
    PdfTool(
        id = "protect",
        title = "Protect PDF",
        description = "Add a password so only someone who knows it can open the file.",
        icon = Icons.Filled.Lock,
        category = ToolCategory.Security,
    ),
    PdfTool(
        id = "unlock",
        title = "Unlock PDF",
        description = "Remove password protection from encrypted PDF files.",
        icon = Icons.Filled.LockOpen,
        category = ToolCategory.Security,
    ),
    PdfTool(
        id = "edit",
        title = "Edit Metadata",
        description = "Modify your PDF's title, author, subject, and keywords.",
        icon = Icons.Filled.Edit,
        category = ToolCategory.EditEnhance,
    ),
    PdfTool(
        id = "watermark",
        title = "Add Watermark",
        description = "Apply a text watermark to every page of your PDF.",
        icon = Icons.Filled.AutoAwesome,
        category = ToolCategory.EditEnhance,
    ),
    PdfTool(
        id = "page-numbers",
        title = "Add Page Numbers",
        description = "Stamp sequential or Bates numbers onto your PDF.",
        icon = Icons.Filled.Numbers,
        category = ToolCategory.EditEnhance,
    ),
    PdfTool(
        id = "images-to-pdf",
        title = "Convert Images to PDF",
        description = "Combine JPEG or PNG images into a single PDF.",
        icon = Icons.Filled.Image,
        category = ToolCategory.ConvertExport,
    ),
)
