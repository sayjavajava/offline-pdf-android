# Changelog

All notable changes to this project are documented here, one bullet per
merged PR under `## [Unreleased]`. Same convention as the sibling web repo's
`CHANGELOG.md`.

## [Unreleased]

### Added

- Project scaffolding: Compose app shell (dashboard + per-tool navigation,
  empty until the first tool ships), pinned dependency versions, CI
  (`assembleDebug`, `lint`, `testDebugUnitTest`, plus a gate that fails the
  build if the `INTERNET` permission ever appears in the merged manifest).
- Shared PDF-tool infrastructure every tool screen builds on: a PDF loader
  that tries a file with no password first and reports whether one is
  needed (wrong or missing) rather than a generic error, Storage-Access-
  Framework file pickers (open one/many, choose a save location) with no
  storage permission requested, and a reusable tool-screen scaffold
  (pick file → optional password → tool options → run → progress →
  result).
- **Split PDF**: extract a page range (e.g. "1, 3-5, 8", or "all") from a
  PDF into a new file, or download each selected page as its own file
  (zipped when there's more than one). Invalid or out-of-range segments
  are named specifically rather than a generic error — "1-3, 99" against a
  5-page document reports 99 as the problem, not the whole range.
- **Merge PDF**: combine two or more PDFs into one, in the order they were
  selected. A file that can't be read — wrong password, not a PDF at all —
  is named specifically rather than a generic "merge failed" error.
- **Rotate Pages**: rotate the whole document, or just the pages you list,
  by 90°, 180°, or 270° — added to each page's current rotation, not set
  absolutely, so rotating an already-rotated page keeps compounding as
  expected.
- **Delete / Reorder**: keep only the pages you list, in that order —
  omitting a page deletes it, listing one more than once duplicates it.
- **Protect PDF**: add an AES-256 password to a PDF. Optionally restrict
  printing, copying, or editing with a separate permissions password — PDF
  readers grant full access to whoever supplies the password used to open
  the file, so a genuine restriction needs two distinct passwords, not one.
- **Unlock PDF**: remove password protection from an encrypted PDF, given
  its password.
- **Redact PDF**: draw boxes over content to permanently delete it — the
  page is rebuilt as a flattened image with no text, image data, or
  annotations underneath the box, so nothing under it stays selectable,
  copyable, or searchable. A drawn box can be copied onto other
  same-sized pages in one step; pages with a different size are skipped
  and named rather than silently mismatched.
- **Edit Metadata**: change a PDF's title, author, subject, and keywords.
  A blank field leaves the existing value untouched — this edits only the
  fields you fill in, it doesn't clear the rest.
- **Add Watermark**: stamp text onto every page of a PDF, once centered
  (with adjustable size, colour, opacity, and rotation) or tiled across
  the whole page.
- **Add Page Numbers**: stamp sequential page numbers, "page x of y", or
  zero-padded Bates numbers, onto some or all pages. Numbering counts from
  the chosen start value across the stamped pages only, not the
  document's absolute page index.
- **Convert Images to PDF**: combine one or more JPEG or PNG images into a
  single PDF, one page per image sized to that image's own dimensions, in
  the order they were selected.
- **Crop / Resize Pages**: crop trims a fixed margin from each edge
  non-destructively (only the visible window shrinks — content and the
  original page size are untouched), while resize actually rescales
  content and page size together to a target paper size (A4, Letter,
  Legal, or custom), defaulting to a centered scale-to-fit so nothing is
  distorted unless you choose to stretch.
- **Fill PDF Forms**: fill in a PDF's fillable text fields, checkboxes,
  dropdowns, and radio buttons, then download the result — flattened so
  it looks identical in every reader, or left editable so the fields can
  still be changed later. Field types this tool doesn't edit (buttons,
  option lists, signature fields) are listed by name rather than hidden
  or silently skipped.
- **Add Signature**: stamp a visual signature — typed, drawn freehand, or
  an uploaded image — onto a page. A visual mark, not a cryptographic
  digital signature. Placement is entered directly in the page's own
  point-space (no live preview yet, pending page rendering).
- **Extract Images**: pull the embedded images out of a PDF without
  modifying it — JPEG images are exported as-is, and PNG-style images are
  re-encoded from their raw pixel data. A single image downloads as
  itself; more than one is bundled into a zip. Images the extractor can't
  safely export (uncommon encodings or colour spaces) are named and
  skipped rather than silently dropped or exported wrong.
- **Extract Text**: pull the text out of a PDF (all pages, or a page
  range) as a plain text file, optionally marked with where each page
  starts. A scanned PDF has no text layer and comes back empty — flagged
  as most likely scanned rather than silently downloading a blank file.
- **Compare PDFs**: find what changed between two versions of a document,
  page by page — text and visual differences are reported independently,
  since one can change without the other. Read-only: nothing is modified,
  and no PDF is produced, just a downloadable report. Differently-sized
  pages skip the text comparison (it can't be trusted there) but are
  still reported as visually different.
- **Compress PDF**: shrink a PDF by recompressing its embedded images as
  JPEG, only replacing an image when the result actually comes out
  smaller — a text-only document, or one that's already efficiently
  compressed, is safely returned unchanged rather than made bigger.
  Lossy for any image that is recompressed, disclosed up front.
- **Convert DOCX to PDF**: convert a Word document into a real,
  text-based PDF — selectable and searchable, not a picture of the page.
  Headings, paragraphs, and bold/italic text are supported. A table or
  image is skipped and reported, not silently dropped; list item text
  still converts (just without its bullet/number marker, which isn't
  preserved yet).
- **PDF to Images**: render a page range (or every page) to PNG at a
  chosen scale (1 = 72dpi). A single page downloads as itself; more than
  one is bundled into a zip.
