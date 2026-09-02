package com.offgridpdf.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The "paper & ink" palette: warm off-white paper, near-black ink, and one
 * accent hue per tool category (terracotta / forest / teal / plum), each
 * with a deeper "label" variant used where the accent sits as text against
 * the page rather than as a filled shape. Values below are sRGB hex
 * conversions of the design's source oklch tokens (same hue/chroma/lightness
 * families used by the reworked-UI mockups) — see
 * `docs/oklch2hex.mjs`-equivalent conversion in the PR description for the
 * source values; oklch isn't representable directly in `Color()`, so the
 * conversion is baked in here rather than recomputed at runtime.
 */
data class OffGridPalette(
    val paper: Color,
    val paperRaised: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val organize: Color,
    val organizeLabel: Color,
    val security: Color,
    val securityLabel: Color,
    val convert: Color,
    val convertLabel: Color,
    val edit: Color,
    val editLabel: Color,
    val onAccent: Color,
)

val LightOffGridPalette = OffGridPalette(
    paper = Color(0xFFFCF5EC),
    paperRaised = Color(0xFFFFFCF8),
    ink = Color(0xFF15181F),
    inkSecondary = Color(0xFF545860),
    inkTertiary = Color(0xFF888C94),
    hairline = Color(0xFFDDD6CD),
    hairlineStrong = Color(0xFFC5BCB1),
    organize = Color(0xFFA14122),
    organizeLabel = Color(0xFF7E2401),
    security = Color(0xFF2E6540),
    securityLabel = Color(0xFF174B2A),
    convert = Color(0xFF25627D),
    convertLabel = Color(0xFF0C4960),
    edit = Color(0xFF6F4676),
    editLabel = Color(0xFF542F5A),
    onAccent = Color(0xFFFDFBF9),
)

val DarkOffGridPalette = OffGridPalette(
    paper = Color(0xFF17130E),
    paperRaised = Color(0xFF231F19),
    ink = Color(0xFFEBE7E2),
    inkSecondary = Color(0xFFA8A49E),
    inkTertiary = Color(0xFF6F6B66),
    hairline = Color(0xFF37322B),
    hairlineStrong = Color(0xFF4D473E),
    organize = Color(0xFFBC593A),
    organizeLabel = Color(0xFFE08C71),
    security = Color(0xFF437E54),
    securityLabel = Color(0xFF75A682),
    convert = Color(0xFF397A97),
    convertLabel = Color(0xFF6AA1BB),
    edit = Color(0xFF885B8F),
    editLabel = Color(0xFFB088B6),
    onAccent = Color(0xFFFAF8F5),
)
