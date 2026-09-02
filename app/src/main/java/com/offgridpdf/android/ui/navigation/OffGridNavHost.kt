package com.offgridpdf.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.offgridpdf.android.chain.PendingNavigation
import com.offgridpdf.android.chain.ROUTE_DASHBOARD
import com.offgridpdf.android.ui.dashboard.DashboardScreen
import com.offgridpdf.android.ui.dashboard.RecentToolsStore
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
private const val ARG_TOOL_ID = "toolId"

@Composable
fun OffGridNavHost() {
    val navController = rememberNavController()

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

    CompositionLocalProvider(LocalNavController provides navController) {
        NavHost(navController = navController, startDestination = ROUTE_DASHBOARD) {
            composable(ROUTE_DASHBOARD) {
                val context = LocalContext.current
                val recentToolsStore = remember { RecentToolsStore(context) }
                DashboardScreen(
                    recentToolsStore = recentToolsStore,
                    onToolSelected = { toolId ->
                        recentToolsStore.recordUsed(toolId)
                        navController.navigate("tool/$toolId")
                    },
                )
            }
            composable(
                route = ROUTE_TOOL,
                arguments = listOf(navArgument(ARG_TOOL_ID) { type = NavType.StringType }),
            ) { backStackEntry ->
                when (val toolId = backStackEntry.arguments?.getString(ARG_TOOL_ID).orEmpty()) {
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
        }
    }
}
