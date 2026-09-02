package com.offgridpdf.android.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController

/**
 * Lets a tool screen's top bar show a real back chevron and pop the back
 * stack without threading an `onBack` callback through every one of the
 * ~20 tool screen composables' signatures — provided once at the NavHost
 * root, consumed by `ToolScaffold` and `RedactScreen`'s own top bar.
 */
val LocalNavController = staticCompositionLocalOf<NavController?> { null }
