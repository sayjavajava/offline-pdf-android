package com.offgridpdf.android.ui.common

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.offgridpdf.android.files.queryDisplayName

/**
 * The real filename [uri] is known by, resolved asynchronously via
 * [queryDisplayName] — see that function's doc for why `uri.lastPathSegment`
 * is not this. Null while [uri] is null or the query is still in flight, so
 * every call site already has a fallback ("Choose a file") for the case
 * where nothing is picked yet, and that same fallback covers the brief gap
 * before a freshly picked file's name resolves.
 *
 * Re-resolves whenever [uri] changes, clearing the previous value first: a
 * stale name for a *different* file, shown for one frame while the new one
 * resolves, would be actively misleading rather than merely blank.
 */
@Composable
fun rememberDisplayName(uri: Uri?): String? {
    val context = LocalContext.current
    var name by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uri) {
        name = null
        name = uri?.let { queryDisplayName(context, it) }
    }
    return name
}

/**
 * The real filenames for [uris], in the same order, resolved the same way
 * as [rememberDisplayName]. Empty while [uris] is empty or its names are
 * still resolving — including right after [uris] changes, so a caller never
 * sees a list of names left over from a previous, different selection.
 */
@Composable
fun rememberDisplayNames(uris: List<Uri>): List<String> {
    val context = LocalContext.current
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(uris) {
        names = emptyList()
        names = uris.map { queryDisplayName(context, it) }
    }
    return names
}
