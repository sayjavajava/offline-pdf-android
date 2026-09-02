package com.offgridpdf.android.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.offgridpdf.android.R

/**
 * Vendored (res/font/) rather than fetched at runtime — same "everything
 * this app needs ships in the APK" rule PdfBox-Android and the OCR
 * language packs already follow. Source Serif 4 / IBM Plex Sans / IBM
 * Plex Mono, all OFL-licensed.
 */
val SourceSerif = FontFamily(
    Font(R.font.source_serif_regular, FontWeight.Normal),
    Font(R.font.source_serif_semibold, FontWeight.SemiBold),
    Font(R.font.source_serif_bold, FontWeight.Bold),
)

val PlexSans = FontFamily(
    Font(R.font.plex_sans_regular, FontWeight.Normal),
    Font(R.font.plex_sans_medium, FontWeight.Medium),
    Font(R.font.plex_sans_semibold, FontWeight.SemiBold),
)

val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
)
