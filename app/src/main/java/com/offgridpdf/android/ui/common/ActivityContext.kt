package com.offgridpdf.android.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Compose's `LocalContext` is not guaranteed to be the Activity itself — it
 * can be a `ContextWrapper` around it — so unwrap rather than casting. Doing
 * this by hand rather than via `LocalActivity` keeps it working regardless of
 * which activity-compose version the project is on.
 *
 * Shared by everything that needs the window rather than just a Context:
 * FLAG_SECURE (`SecureScreenEffect`) and the system bar icon colours
 * (`SystemBarAppearanceEffect`).
 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
