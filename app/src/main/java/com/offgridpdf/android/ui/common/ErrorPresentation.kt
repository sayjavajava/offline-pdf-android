package com.offgridpdf.android.ui.common

/**
 * Maps a caught exception to a message a user should actually see — this
 * app's equivalent of the web app's own discipline (never a raw stack
 * trace in a toast). Deliberately small for now: `IOException` covers
 * every failure mode `PdfLoader.kt`/`DocumentPicker.kt` currently produce.
 * Extend this, don't scatter ad-hoc `.message ?: "..."` fallbacks in
 * screens, as later tools introduce their own failure modes.
 */
fun userMessageFor(throwable: Throwable): String =
    throwable.message?.takeIf { it.isNotBlank() }
        ?: "Something went wrong (${throwable::class.simpleName})."
