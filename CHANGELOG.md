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
