package com.offgridpdf.android.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.ByteArrayOutputStream

/** How much printing is allowed for a reader who only has the open password. */
enum class PrintPermission { NONE, LOW, FULL }

/**
 * Allow document modification. qpdf's own scale has five levels
 * (none/assembly/form/annotate/all); the web app's UI collapses that to a
 * binary for v1 (`qpdf-engine.ts`'s `PdfPermissions` type) — matched here
 * rather than exposing more granularity the web version doesn't have.
 */
enum class ModifyPermission { NONE, ALL }

/** Web reference: `PdfPermissions` (`qpdf-engine.ts`). */
data class PdfPermissions(
    val print: PrintPermission,
    val extract: Boolean,
    val modify: ModifyPermission,
)

private const val MIN_PASSWORD_LENGTH = 4

/**
 * Adds a single, shared open password to [document] — the counterpart with
 * enforceable restrictions is [protectPdfWithPermissions]. Web reference:
 * `protectPdf` (`pdf-ops.ts`).
 *
 * AES-256 via `StandardProtectionPolicy`, the exact recipe
 * `NATIVE_ANDROID_SPIKE.md` (tool-docs repo) verified end to end against
 * this project's own qpdf oracle before it was trusted here.
 */
fun protectPdf(document: PDDocument, password: String): ByteArray {
    if (password.isEmpty()) {
        throw IllegalArgumentException("Enter a password.")
    }
    if (password.length < MIN_PASSWORD_LENGTH) {
        throw IllegalArgumentException("Use a password of at least $MIN_PASSWORD_LENGTH characters.")
    }

    val policy = StandardProtectionPolicy(password, password, AccessPermission())
    policy.setEncryptionKeyLength(256)
    policy.setPreferAES(true)
    document.protect(policy)

    val out = ByteArrayOutputStream()
    document.save(out)
    return out.toByteArray()
}

/**
 * Adds distinct open/permissions passwords and restriction flags to
 * [document]. Web reference: `protectPdfWithPermissions` (`pdf-ops.ts`) /
 * `encryptPdfBytesWithPermissions` (`qpdf-engine.ts`).
 *
 * Restrictions are enforced only for whoever opens with [openPassword] —
 * [permissionsPassword] (the PDF spec's "owner" password) bypasses them
 * entirely, same PDF-spec split the web version's own comment documents.
 * Rejecting `permissionsPassword == openPassword` prevents producing a file
 * that looks protected but enforces nothing.
 *
 * [openPassword] may be empty — "anyone can open it, but can't print or
 * copy without the permissions password" is a real, intentional pattern,
 * not an oversight to guard against.
 */
fun protectPdfWithPermissions(
    document: PDDocument,
    openPassword: String,
    permissionsPassword: String,
    permissions: PdfPermissions,
): ByteArray {
    if (permissionsPassword.isEmpty()) {
        throw IllegalArgumentException("Enter a permissions password.")
    }
    if (permissionsPassword.length < MIN_PASSWORD_LENGTH) {
        throw IllegalArgumentException("Use a permissions password of at least $MIN_PASSWORD_LENGTH characters.")
    }
    if (permissionsPassword == openPassword) {
        throw IllegalArgumentException(
            "The permissions password must differ from the open password — otherwise anyone who can open the file can also bypass every restriction.",
        )
    }

    val accessPermission = AccessPermission()
    // Bit 3 (print at all) and bit 12 (faithful/high-quality print, only
    // meaningful if bit 3 is also set) — confirmed against PDFBox 2.0.x's
    // real source (pdfbox.apache.org is blocked in this sandbox;
    // raw.githubusercontent.com is not) before trusting this mapping.
    accessPermission.setCanPrint(permissions.print != PrintPermission.NONE)
    accessPermission.setCanPrintFaithful(permissions.print == PrintPermission.FULL)
    accessPermission.setCanExtractContent(permissions.extract)
    accessPermission.setCanModify(permissions.modify == ModifyPermission.ALL)

    // Owner password = permissions password (bypasses restrictions), user
    // password = open password (restrictions apply) — the same open/
    // permissions -> user/owner mapping qpdf's own `--encrypt open perms
    // 256` argument order uses.
    val policy = StandardProtectionPolicy(permissionsPassword, openPassword, accessPermission)
    policy.setEncryptionKeyLength(256)
    policy.setPreferAES(true)
    document.protect(policy)

    val out = ByteArrayOutputStream()
    document.save(out)
    return out.toByteArray()
}
