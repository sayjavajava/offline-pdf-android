package com.offgridpdf.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * These are real assertions rather than a screenshot to eyeball. Each
 * option is scrolled to and then required to be displayed, and that pair
 * is what makes the check meaningful: these screens scroll vertically, so
 * being below the fold is normal and `performScrollTo` fixes it, while a
 * chip pushed off the *right* edge by a non-wrapping `Row` cannot be
 * reached by scrolling a vertical container at all and still fails
 * `assertIsDisplayed`. Before the fix that was four of Add Page Numbers'
 * six positions, the third of its three formats, Protect's "Not allowed"
 * print permission, and — worst — Crop/Resize's "Custom" paper size,
 * which took that tool's entire custom-dimensions feature off screen.
 *
 * (Asserting displayed-ness *without* scrolling first is what the first
 * version of this test did, and it failed in CI for the honest reason
 * that the later option groups start below the fold on a phone.)
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

    /** Scroll each label into view, then require it to actually be visible. */
    private fun assertAllReachable(vararg labels: String) {
        for (label in labels) {
            composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun everyPageNumberPositionIsReachable() {
        composeRule.setContent { OffGridPdfTheme { PageNumbersScreen() } }
        assertAllReachable(
            "Bottom centre",
            "Bottom left",
            "Bottom right",
            "Top centre",
            "Top left",
            "Top right",
        )
    }

    @Test
    fun everyPageNumberFormatIsReachable() {
        composeRule.setContent { OffGridPdfTheme { PageNumbersScreen() } }
        assertAllReachable(
            "Page number (1, 2, 3...)",
            "Page x of y",
            "Bates (zero-padded)",
        )
    }

    @Test
    fun everyPaperSizeIncludingCustomIsReachable() {
        composeRule.setContent { OffGridPdfTheme { CropResizeScreen() } }
        // Resize mode is where the paper sizes live; Crop is the default.
        composeRule.onNodeWithText("Resize").performScrollTo().performClick()
        assertAllReachable("A4", "Letter", "Legal", "Custom")
    }

    @Test
    fun everyPrintPermissionIsReachable() {
        composeRule.setContent { OffGridPdfTheme { ProtectScreen() } }
        composeRule.onNodeWithText("Restrict printing, copying, or editing").performScrollTo().performClick()
        assertAllReachable("Full quality", "Low resolution only", "Not allowed")
    }
}
