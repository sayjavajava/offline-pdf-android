package com.offgridpdf.android.ui.dashboard

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.offgridpdf.android.R
import com.offgridpdf.android.ui.theme.OffGridPalette

/**
 * Same 4-category grouping as the web app's `GlassDashboard.tsx` /
 * `pdfTools` array — keep this list and that one in sync by hand; there is
 * no shared source of truth between the two codebases (they're a rewrite,
 * not a shared package). See `ANDROID_IMPLEMENTATION_PLAN.md` (tool-docs
 * repo) for the full 19-tool list and the order they're implemented in.
 *
 * Each category carries its own accent hue from the "paper & ink" redesign
 * (see the UI redesign mockups): [accent] tints a tool's icon and fills a
 * tool screen's primary button; [labelAccent] is the deeper/lighter (theme-
 * dependent) variant used where the accent sits as text directly on the
 * page, such as a category heading.
 */
enum class ToolCategory(val label: String) {
    OrganizePages("Organize Pages") {
        override fun accent(palette: OffGridPalette) = palette.organize
        override fun labelAccent(palette: OffGridPalette) = palette.organizeLabel
    },
    Security("Security") {
        override fun accent(palette: OffGridPalette) = palette.security
        override fun labelAccent(palette: OffGridPalette) = palette.securityLabel
    },
    ConvertExport("Convert & Export") {
        override fun accent(palette: OffGridPalette) = palette.convert
        override fun labelAccent(palette: OffGridPalette) = palette.convertLabel
    },
    EditEnhance("Edit & Enhance") {
        override fun accent(palette: OffGridPalette) = palette.edit
        override fun labelAccent(palette: OffGridPalette) = palette.editLabel
    },
    ;

    abstract fun accent(palette: OffGridPalette): Color
    abstract fun labelAccent(palette: OffGridPalette): Color
}

data class PdfTool(
    val id: String,
    val title: String,
    val description: String,
    @DrawableRes val icon: Int,
    val category: ToolCategory,
)

val pdfTools: List<PdfTool> = listOf(
    PdfTool(
        id = "split",
        title = "Split PDF",
        description = "Break a document into separate files by page range.",
        icon = R.drawable.ic_split,
        category = ToolCategory.OrganizePages,
    ),
    PdfTool(
        id = "merge",
        title = "Merge PDF",
        description = "Combine multiple PDF documents into a single file.",
        icon = R.drawable.ic_merge,
        category = ToolCategory.OrganizePages,
    ),
    PdfTool(
        id = "rotate",
        title = "Rotate Pages",
        description = "Rotate selected pages by 90°, 180°, or 270°.",
        icon = R.drawable.ic_rotate,
        category = ToolCategory.OrganizePages,
    ),
    PdfTool(
        id = "rearrange",
        title = "Delete / Reorder",
        description = "Keep pages in a custom order; omit pages to delete them.",
        icon = R.drawable.ic_rearrange,
        category = ToolCategory.OrganizePages,
    ),
    PdfTool(
        id = "crop-resize",
        title = "Crop / Resize Pages",
        description = "Trim margins non-destructively, or rescale pages to a target size.",
        icon = R.drawable.ic_crop_resize,
        category = ToolCategory.OrganizePages,
    ),
    PdfTool(
        id = "protect",
        title = "Protect PDF",
        description = "Add a password so only someone who knows it can open the file.",
        icon = R.drawable.ic_protect,
        category = ToolCategory.Security,
    ),
    PdfTool(
        id = "unlock",
        title = "Unlock PDF",
        description = "Remove password protection from encrypted PDF files.",
        icon = R.drawable.ic_unlock,
        category = ToolCategory.Security,
    ),
    PdfTool(
        id = "redact",
        title = "Redact PDF",
        description = "Permanently remove content under boxes you draw — deleted, not just covered.",
        icon = R.drawable.ic_redact,
        category = ToolCategory.Security,
    ),
    PdfTool(
        id = "edit",
        title = "Edit Metadata",
        description = "Modify your PDF's title, author, subject, and keywords.",
        icon = R.drawable.ic_edit_metadata,
        category = ToolCategory.EditEnhance,
    ),
    PdfTool(
        id = "watermark",
        title = "Add Watermark",
        description = "Apply a text watermark to every page of your PDF.",
        icon = R.drawable.ic_watermark,
        category = ToolCategory.EditEnhance,
    ),
    PdfTool(
        id = "page-numbers",
        title = "Add Page Numbers",
        description = "Stamp sequential or Bates numbers onto your PDF.",
        icon = R.drawable.ic_page_numbers,
        category = ToolCategory.EditEnhance,
    ),
    PdfTool(
        id = "images-to-pdf",
        title = "Convert Images to PDF",
        description = "Combine JPEG or PNG images into a single PDF.",
        icon = R.drawable.ic_images_to_pdf,
        category = ToolCategory.ConvertExport,
    ),
    PdfTool(
        id = "docx-to-pdf",
        title = "Convert DOCX to PDF",
        description = "Convert a Word document to a real, text-based PDF.",
        icon = R.drawable.ic_docx_to_pdf,
        category = ToolCategory.ConvertExport,
    ),
    PdfTool(
        id = "pdf-to-images",
        title = "PDF to Images",
        description = "Render PDF pages to PNG images.",
        icon = R.drawable.ic_pdf_to_images,
        category = ToolCategory.ConvertExport,
    ),
    PdfTool(
        id = "extract-images",
        title = "Extract Images",
        description = "Pull the embedded images out of a PDF, without changing it.",
        icon = R.drawable.ic_extract_images,
        category = ToolCategory.ConvertExport,
    ),
    PdfTool(
        id = "extract-text",
        title = "Extract Text",
        description = "Pull the text out of a PDF as a plain text file.",
        icon = R.drawable.ic_extract_text,
        category = ToolCategory.ConvertExport,
    ),
    PdfTool(
        id = "compare",
        title = "Compare PDFs",
        description = "Find what changed between two versions — page by page, text and visual.",
        icon = R.drawable.ic_compare,
        category = ToolCategory.ConvertExport,
    ),
    PdfTool(
        id = "fill-form",
        title = "Fill PDF Forms",
        description = "Fill in text fields, checkboxes, dropdowns, and radio buttons, then download the result.",
        icon = R.drawable.ic_fill_form,
        category = ToolCategory.EditEnhance,
    ),
    PdfTool(
        id = "compress",
        title = "Compress PDF",
        description = "Shrink a PDF, mainly by recompressing its embedded images.",
        icon = R.drawable.ic_compress,
        category = ToolCategory.EditEnhance,
    ),
    PdfTool(
        id = "signature",
        title = "Add Signature",
        description = "Type, draw, or upload a signature and place it on a page.",
        icon = R.drawable.ic_signature,
        category = ToolCategory.EditEnhance,
    ),
)
