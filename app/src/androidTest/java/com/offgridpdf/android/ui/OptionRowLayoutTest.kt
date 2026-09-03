package com.offgridpdf.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.offgridpdf.android.ui.theme.OffGridPdfTheme
import com.offgridpdf.android.ui.tool.CropResizeScreen
import com.offgridpdf.android.ui.tool.PageNumbersScreen
import com.offgridpdf.android.ui.tool.ProtectScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the specific regression the form/layout pass fixed: an option row
 * wide enough to push its own options off the edge of the display.
 *
 * These are real assertions rather than a screenshot to eyeball, because
 * `assertIsDisplayed()` is precisely the check that fails for a node the
 * layout has placed outside the window — which is exactly what a
 * non-wrapping `Row` of Material buttons did here. Before the fix, four of
 * Add Page Numbers' six position options, the third of its three formats,
 * Protect's "Not allowed" print permission, and — worst — Crop/Resize's
 * "Custom" paper size were all laid out past the right edge, unreachable
 * on a phone with no way to scroll horizontally to them. The "Custom" one
 * took that tool's entire custom-dimensions feature off screen with it.
 *
 * Each screen is composed on its own rather than driven through
 * navigation: these assert one thing, that every option in a group is
 * actually on screen, and a tool screen with nothing picked composes its
 * whole options block regardless.
 */
@RunWith(AndroidJUnit4::class)
class OptionRowLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun assertAllDisplayed(vararg labels: String) {
        for (label in labels) {
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun everyPageNumberPositionIsOnScreen() {
        composeRule.setContent { OffGridPdfTheme { PageNumbersScreen() } }
        assertAllDisplayed(
            "Bottom centre",
            "Bottom left",
            "Bottom right",
            "Top centre",
            "Top left",
            "Top right",
        )
    }

    @Test
    fun everyPageNumberFormatIsOnScreen() {
        composeRule.setContent { OffGridPdfTheme { PageNumbersScreen() } }
        assertAllDisplayed(
            "Page number (1, 2, 3...)",
            "Page x of y",
            "Bates (zero-padded)",
        )
    }

    @Test
    fun everyPaperSizeIncludingCustomIsOnScreen() {
        composeRule.setContent { OffGridPdfTheme { CropResizeScreen() } }
        // Resize mode is where the paper sizes live; Crop is the default.
        composeRule.onNodeWithText("Resize").performClick()
        assertAllDisplayed("A4", "Letter", "Legal", "Custom")
    }

    @Test
    fun everyPrintPermissionIsOnScreen() {
        composeRule.setContent { OffGridPdfTheme { ProtectScreen() } }
        composeRule.onNodeWithText("Restrict printing, copying, or editing").performClick()
        assertAllDisplayed("Full quality", "Low resolution only", "Not allowed")
    }
}
