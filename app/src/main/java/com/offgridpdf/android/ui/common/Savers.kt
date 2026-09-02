package com.offgridpdf.android.ui.common

import android.net.Uri
import androidx.compose.runtime.saveable.Saver

/**
 * Savers for the two types tool screens keep in `rememberSaveable` that
 * Compose's own automatic saver will not take.
 *
 * `rememberSaveable` writes through to the Activity's saved instance state,
 * which is a `Bundle`, so a value has to be something a `Bundle` can hold.
 * `Uri` is `Parcelable` and would survive on its own, but a `List<Uri>` will
 * not: the automatic saver checks the concrete collection class, and
 * `listOf(...)` is not the `ArrayList` a `Bundle` accepts. Rather than rely on
 * that distinction holding, both go through strings — a `Uri` round-trips
 * through `toString`/`parse` exactly, and the result is obviously
 * bundle-safe.
 *
 * What this preserves is process death (the system reclaiming the app while
 * it is in the background, then rebuilding it when the user returns).
 * Rotation is handled separately and better, by `android:configChanges` on
 * MainActivity — see the comment there.
 *
 * Three kinds of state are deliberately left in plain `remember`, and should
 * stay there:
 *
 *  - **Passwords.** Saved instance state is written out by the system and can
 *    reach disk. A document password is the one thing on these screens that
 *    is genuinely a secret, and `configChanges` already carries it across a
 *    rotation, so nothing is gained by saving it.
 *  - **Anything that only makes sense next to a value that cannot be saved**
 *    — an open `PDDocument`, a rendered `ImageBitmap`, result `ByteArray`s
 *    (a `Bundle` is capped around 1 MB), a running `Job`. Restoring half of
 *    such a pair puts the screen into a state it can never reach on its own:
 *    a page count for a document that is no longer open, form values behind
 *    a file picker.
 *  - **In-flight flags** (`running`, `loading`, `applying`, ...). The work
 *    they describe does not survive process death, so restoring `true` would
 *    leave a spinner that never stops.
 */

/** For a screen's single picked file. Empty string encodes "nothing picked". */
val NullableUriSaver: Saver<Uri?, String> = Saver(
    save = { it?.toString().orEmpty() },
    restore = { stored -> if (stored.isEmpty()) null else Uri.parse(stored) },
)

/** For the multi-file pickers (Merge, Compare, Images to PDF, and batch mode). */
val UriListSaver: Saver<List<Uri>, ArrayList<String>> = Saver(
    save = { uris -> ArrayList(uris.map(Uri::toString)) },
    restore = { stored -> stored.map(Uri::parse) },
)
