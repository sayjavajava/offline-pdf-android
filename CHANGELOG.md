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
