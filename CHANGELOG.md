# Changelog

All notable changes to this project are documented here, under
`## [Unreleased]` until there is a release to name. Same convention as the
sibling web repo's `CHANGELOG.md`: an entry for anything **user-facing** — a
new tool, a behaviour change, a bug someone would notice. Internal-only
changes (CI, tooling, refactors, spikes, release plumbing) don't get one.

Nothing has been released yet: `versionCode` is still 1 and no tag has been
cut, so everything below is in the first release when it happens.

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
  and named rather than silently mismatched. Or search for text across
  the whole document and turn every occurrence into a reviewable box
  automatically — nothing is applied until you hit Apply, same as a
  hand-drawn box; a match spanning a line break is skipped and counted
  rather than guessed at.
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
  digital signature. Place it by dragging on the rendered page, or by
  typing exact coordinates in the page's own point-space — the two are
  bound both ways, so dragging rewrites the fields and typing moves the
  outline. The placement is outlined rather than filled, so you can see
  what the signature will sit on top of.
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
- **Open a PDF from another app**: OffGridPDF now appears in Android's
  "open with" and "share" sheets for PDFs. The file arrives on the
  dashboard ready to hand to any tool.
- **Home-screen shortcuts** for the tools you use most, kept in step with
  a "Recent" row on the dashboard.
- **Tool chaining**: "Continue with another tool" takes a result straight
  into the next tool without saving and re-picking it. The chained file is
  named from the *original* file plus the latest operation, so a result
  never accumulates a trail of suffixes across hops.
- **In-app theme toggle** — System, Light, or Dark, remembered across
  launches, in a new Settings screen.
- **Two-pane layout on tablets and wide windows**: the tool list and the
  open tool sit side by side instead of the phone layout being stretched
  across the screen.
- **Batch mode** for Compress, Watermark, Rotate, and Add Page Numbers:
  pick several PDFs and apply the same operation to each in one run. A
  file that fails is named and the rest still run.
- **Crop preview**: "Preview the crop" renders the first page the range
  covers and draws the boundary on it, updating live as the margins are
  typed. Outlined rather than filled, because a crop only moves the
  CropBox — the content outside it is still in the file, and covering it
  would tell the wrong story about what the tool does. Resize has no
  preview: it rescales content and page size together, so the page looks
  the same and only its dimensions change.
- **Completion feedback and sharing**: a run says clearly when it has
  finished, and the result can be shared straight to another app.
- **Opt-in "block screen capture" setting** (Settings): turns on
  `FLAG_SECURE`, so the app's contents are excluded from screenshots and
  the recent-apps thumbnail. Off by default.

### Changed

- **The whole UI was reworked into a "paper & ink" editorial design**: a
  warm paper ground with near-black ink, one accent colour per tool
  category, vendored typefaces (Source Serif 4 / IBM Plex Sans / IBM Plex
  Mono), and a hand-drawn line icon per tool in place of stock Material
  icons. The dashboard gained a masthead, a working search filter, and a
  hairline category list.
- **Every tool screen now uses the same set of form controls** — one text
  field, one option chip, one checkbox row, one section label — instead of
  each screen styling its own. Six screens that had missed the redesign
  were brought onto it at the same time.
- Bar icons are now large enough to hit reliably, meeting the 48dp
  minimum touch target.

### Fixed

- **Text-drawing tools would have crashed on a real device.** PdfBox-Android
  needs `PDFBoxResourceLoader.init()` before any standard-14-font metrics
  lookup, and nothing had ever called it, so Watermark, Add Page Numbers,
  Convert DOCX to PDF, and Add Signature's typed mode all failed the first
  time they ran outside a JVM unit test. Now initialised at app startup.
- **Convert DOCX to PDF silently produced a blank document.** A namespace
  mismatch meant the document's paragraphs were never found, so the tool
  reported success and wrote an empty PDF.
- **The app no longer freezes or crashes while a tool runs.** All PDF work
  moved off the main thread, and failures now surface as a message on the
  screen instead of taking the app down.
- **Page ranges and incoming intents are validated.** A range like
  "1-999999999" against a small document no longer tries to allocate its
  way to a crash, and a malformed intent from another app is rejected
  rather than trusted.
- **Large documents no longer accumulate page rasters** until the app runs
  out of memory — rendered pages are bounded and released.
- **Tool state survives rotation and process death.** Turning the phone
  mid-task no longer clears the file you picked and the options you set.
  Document passwords are deliberately excluded: they are never written to
  saved instance state.
- **The chain cache is cleared at startup**, so a file left mid-chain by a
  previous session doesn't persist. Shortcut updates moved off the main
  thread, and the flash of the wrong theme at launch is gone.
- **Content no longer sits under the status and navigation bars.**
- **Picked files show their real name** instead of a provider's internal
  document id.
- **Option rows no longer run off the edge of the screen** — most visibly
  the Crop/Resize "Custom" paper size, which was genuinely unreachable on
  a narrow screen.
- Cancelling a page render (by changing pages mid-render) no longer
  reports itself as a render failure.
- The Signature screen now scrolls, and closes the PDF it holds open when
  you navigate away instead of leaking it.

### Security

- Bumped Bouncy Castle 1.72 → 1.85, clearing the CVEs in the older release.
