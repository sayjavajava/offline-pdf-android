package com.offgridpdf.android.ui.common

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.offgridpdf.android.R
import com.offgridpdf.android.files.SavedFile
import com.offgridpdf.android.files.shareFileIntent
import com.offgridpdf.android.ui.theme.LocalOffGridPalette
import kotlinx.coroutines.launch

/**
 * What a tool screen shows once a run has ended.
 *
 * Before this, both outcomes were one line of small grey text at the bottom
 * of the screen: "Your PDF has been split successfully." looked exactly like
 * "Could not open the chosen location for writing.", and neither read as
 * "this is finished". People could not tell whether anything had happened.
 *
 * So a finished run gets a banner it is hard to miss, in the tool's own
 * accent colour, naming the file that was written and offering the two
 * things anyone wants next: send it somewhere, or feed it into another tool.
 * A failure gets the same shape in a plain, quieter treatment -- there is
 * nothing to act on, so it carries no actions.
 */
@Composable
fun ToolCompletion(
    message: String,
    savedFile: SavedFile?,
    accent: Color,
    modifier: Modifier = Modifier,
    chainableBytes: ByteArray? = null,
    // Unused unless chainableBytes is non-null -- ContinueChainAction
    // returns before touching either, so the empty-string default is never
    // observable.
    chainOriginBaseName: String = "",
    chainedFileName: String = "",
) {
    val palette = LocalOffGridPalette.current
    val succeeded = savedFile != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (succeeded) accent.copy(alpha = 0.10f) else palette.paperRaised)
            .border(
                BorderStroke(1.dp, if (succeeded) accent else palette.hairlineStrong),
                RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (succeeded) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (succeeded) palette.ink else palette.inkSecondary,
                )
                savedFile?.let {
                    Text(
                        "Saved as ${it.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkTertiary,
                    )
                }
            }
        }

        if (savedFile != null) {
            ShareAction(file = savedFile, accent = accent)
        }
        if (succeeded) {
            ContinueChainAction(
                bytes = chainableBytes,
                accent = accent,
                originBaseName = chainOriginBaseName,
                chainedFileName = chainedFileName,
            )
        }
    }
}

/**
 * Opens the system share sheet with the file that was just written.
 *
 * The chooser is what decides where it goes, so this app never picks a
 * destination -- and nothing is sent anywhere unless the user chooses an app
 * in the sheet.
 */
@Composable
private fun ShareAction(file: SavedFile, accent: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(BorderStroke(1.dp, accent), RoundedCornerShape(10.dp))
            .clickable {
                scope.launch {
                    val intent = shareFileIntent(context, file)
                    context.startActivity(Intent.createChooser(intent, "Share file"))
                }
            }
            .padding(vertical = 13.dp, horizontal = 14.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_share),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp),
        )
        Text(
            "Share",
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            modifier = Modifier.weight(1f),
        )
    }
}
