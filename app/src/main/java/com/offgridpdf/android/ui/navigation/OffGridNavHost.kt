package com.offgridpdf.android.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.offgridpdf.android.chain.PendingNavigation
import com.offgridpdf.android.chain.ROUTE_DASHBOARD
import com.offgridpdf.android.ui.dashboard.DashboardScreen
import com.offgridpdf.android.ui.dashboard.RecentToolsStore
import com.offgridpdf.android.ui.settings.SettingsScreen
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import com.offgridpdf.android.ui.tool.CompareScreen
import com.offgridpdf.android.ui.tool.CompressScreen
import com.offgridpdf.android.ui.tool.CropResizeScreen
import com.offgridpdf.android.ui.tool.DocxToPdfScreen
import com.offgridpdf.android.ui.tool.EditMetadataScreen
import com.offgridpdf.android.ui.tool.ExtractImagesScreen
import com.offgridpdf.android.ui.tool.ExtractTextScreen
import com.offgridpdf.android.ui.tool.FillFormScreen
import com.offgridpdf.android.ui.tool.ImagesToPdfScreen
import com.offgridpdf.android.ui.tool.MergeScreen
import com.offgridpdf.android.ui.tool.PageNumbersScreen
import com.offgridpdf.android.ui.tool.PdfToImagesScreen
import com.offgridpdf.android.ui.tool.ProtectScreen
import com.offgridpdf.android.ui.tool.RearrangeScreen
import com.offgridpdf.android.ui.tool.RedactScreen
import com.offgridpdf.android.ui.tool.RotateScreen
import com.offgridpdf.android.ui.tool.SignatureScreen
import com.offgridpdf.android.ui.tool.SplitScreen
import com.offgridpdf.android.ui.tool.ToolPlaceholderScreen
import com.offgridpdf.android.ui.tool.UnlockScreen
import com.offgridpdf.android.ui.tool.WatermarkScreen

private const val ROUTE_TOOL = "tool/{toolId}"
private const val ROUTE_SETTINGS = "settings"
private const val ARG_TOOL_ID = "toolId"

/**
 * Material's own "medium" width-class breakpoint — below it, a phone-style
 * single pane; at or above it (tablets, unfolded foldables, desktop
 * windows), there's enough room for a persistent list pane alongside a
 * detail pane, so browsing the tool list no longer costs the current
 * screen. `LocalConfiguration.screenWidthDp` rather than the
 * `androidx.window` size-class library — this app already reads
 * configuration directly (`RedactScreen.kt`'s own layout math), and a
 * width threshold is all a two-pane split needs.
 */
private const val EXPANDED_WIDTH_THRESHOLD_DP = 600
private val LIST_PANE_WIDTH = 360.dp

/**
 * One `NavController`/graph regardless of pane count — [PendingNavigation]-
 * driven jumps (tool chaining, shortcuts, share/view intents) and the back
 * chevron (`LocalNavController`, `ScreenTopBar`) work identically whether
 * that graph is the whole screen (compact) or living in the right-hand
 * detail pane (expanded). The only thing that differs by width is what the
 * "dashboard" destination itself renders: the real tool list on compact
 * (there's nowhere else for it to live), or [EmptyDetailPlaceholder] on
 * expanded — the list pane there is a second, always-visible
 * `DashboardScreen` instance outside the graph entirely, so the graph's own
 * dashboard destination would otherwise duplicate it.
 */
@Composable
fun OffGridNavHost() {
    val navController = rememberNavController()
    val isExpanded = LocalConfiguration.current.screenWidthDp >= EXPANDED_WIDTH_THRESHOLD_DP
    val context = LocalContext.current
    val recentToolsStore = remember { RecentToolsStore(context) }

    // Consumes a shortcut launch (jump straight to a tool) or an incoming
    // VIEW/SEND intent (jump to the dashboard so its pending-file banner
    // shows) set by MainActivity before this NavHost existed to navigate
    // through directly. Keyed on the value itself so it fires once per new
    // target, not on every recomposition.
    val pendingTarget = PendingNavigation.target
    LaunchedEffect(pendingTarget) {
        if (pendingTarget != null) {
            PendingNavigation.consume()
            navController.navigate(pendingTarget) {
                popUpTo(ROUTE_DASHBOARD) { inclusive = pendingTarget == ROUTE_DASHBOARD }
                launchSingleTop = true
            }
        }
    }

    val graph: @Composable () -> Unit = {
        NavHost(navController = navController, startDestination = ROUTE_DASHBOARD) {
            composable(ROUTE_DASHBOARD) {
                if (isExpanded) {
                    EmptyDetailPlaceholder()
                } else {
                    DashboardScreen(
                        recentToolsStore = recentToolsStore,
                        onToolSelected = { toolId ->
                            recentToolsStore.recordUsed(toolId)
                            navController.navigate("tool/$toolId")
                        },
                        onSettingsClick = { navController.navigate(ROUTE_SETTINGS) },
                    )
                }
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen()
            }
            composable(
                route = ROUTE_TOOL,
                arguments = listOf(navArgument(ARG_TOOL_ID) { type = NavType.StringType }),
            ) { backStackEntry ->
                ToolDestination(backStackEntry.arguments?.getString(ARG_TOOL_ID).orEmpty())
            }
        }
    }

    CompositionLocalProvider(LocalNavController provides navController) {
        if (isExpanded) {
            val palette = LocalOffGridPalette.current
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.width(LIST_PANE_WIDTH)) {
                    DashboardScreen(
                        recentToolsStore = recentToolsStore,
                        onToolSelected = { toolId ->
                            recentToolsStore.recordUsed(toolId)
                            // Selecting a tool always REPLACES the detail
                            // pane rather than stacking on top of it — a
                            // list selection, not a forward navigation —
                            // so the back stack stays exactly
                            // [dashboard, tool] no matter how many tools
                            // get picked in a row.
                            navController.navigate("tool/$toolId") {
                                popUpTo(ROUTE_DASHBOARD)
                                launchSingleTop = true
                            }
                        },
                        onSettingsClick = {
                            navController.navigate(ROUTE_SETTINGS) {
                                popUpTo(ROUTE_DASHBOARD)
                                launchSingleTop = true
                            }
                        },
                    )
                }
                VerticalDivider(color = palette.hairline, thickness = 1.dp)
                Box(modifier = Modifier.weight(1f)) {
                    graph()
                }
            }
        } else {
            graph()
        }
    }
}

@Composable
private fun ToolDestination(toolId: String) {
    when (toolId) {
        "split" -> SplitScreen()
        "merge" -> MergeScreen()
        "rotate" -> RotateScreen()
        "rearrange" -> RearrangeScreen()
        "crop-resize" -> CropResizeScreen()
        "protect" -> ProtectScreen()
        "unlock" -> UnlockScreen()
        "redact" -> RedactScreen()
        "edit" -> EditMetadataScreen()
        "watermark" -> WatermarkScreen()
        "page-numbers" -> PageNumbersScreen()
        "images-to-pdf" -> ImagesToPdfScreen()
        "docx-to-pdf" -> DocxToPdfScreen()
        "pdf-to-images" -> PdfToImagesScreen()
        "extract-images" -> ExtractImagesScreen()
        "extract-text" -> ExtractTextScreen()
        "compare" -> CompareScreen()
        "fill-form" -> FillFormScreen()
        "signature" -> SignatureScreen()
        "compress" -> CompressScreen()
        else -> ToolPlaceholderScreen(toolId = toolId)
    }
}
