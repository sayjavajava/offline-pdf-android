package com.offgridpdf.android.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.R
import com.offgridpdf.android.ui.theme.LocalOffGridPalette

/**
 * The right-hand detail pane's resting state on wide screens
 * (`OffGridNavHost.kt`) — shown until a tool row is tapped in the
 * always-visible list pane. Never shown on a phone-width screen, where the
 * dashboard destination renders the real tool list instead.
 */
@Composable
fun EmptyDetailPlaceholder() {
    val palette = LocalOffGridPalette.current
    Column(
        modifier = Modifier.fillMaxSize().background(palette.paper).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_brand_mark),
            contentDescription = null,
            tint = palette.hairlineStrong,
            modifier = Modifier.size(44.dp),
        )
        Text(
            "Select a tool",
            style = MaterialTheme.typography.headlineSmall,
            color = palette.inkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            "Pick anything from the list to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.inkTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
