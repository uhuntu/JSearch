# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The 1.2.3 / 2.0.0.0 entries below are the **2002 product**. Unreleased entries
are archive housekeeping. This repo does not version a live search service.

## [Unreleased]

### Changed
- Stated the project identity: archive plus teaching sketch, not a search product
- `ROADMAP.md`, `CONTRIBUTING.md`, `MIGRATION.md`, and related docs now match that scope

### Added (archive pass)
- `docs/ARCHITECTURE.md` — Mermaid diagrams comparing original vs modern design
- `docs/ANALYSIS.md` — Detailed code analysis extracted from README
- `CONTRIBUTING.md` — Guidelines for contributing to this archive
- `LICENSE` — GNU General Public License v2.0
- `modern/build.sh` — Cross-platform shell build script (Linux/Mac)
- `.github/workflows/tests.yml` — GitHub Actions CI for automated testing
- `.gitattributes` — Protect GBK-encoded files from accidental conversion

### Changed (archive pass)
- `modern/README.md` — Updated build instructions to reference build scripts
- `README.md` — Added "Further reading" section linking to new documentation

## [2.0.0.0] - 2002-03

### Fixed
- Deadlock patch in `SearchThread.showResult()` — replaced per-instance lock
  with shared static `resultLock` and `searchCountLock`
- TOCTOU race in result collection
- Lost update on total count
- Non-atomic decrement of `actualSearchAllowed`

### Changed
- Rewrote from 1,512 lines (v1.2.3) to 906 lines
- Removed `PingWatch`/`PingThread` asynchronous validation subsystem
- Replaced with synchronous per-result TCP connect
- Deleted ~20 `finalize()` overrides that forced full GC
- Eliminated `Thread.stop()` in favour of `_stop` boolean
- Standardized error messages

### Added
- `smsCh` "Search max speed" — caps simultaneous connections
- `_readTxt` applet parameter — gates blocking file reads at startup
- Helper methods: `getTxtFile()`, `switchPSM()`, `switchEOA()`, `clearAll()`,
  `stopSearch()`, `newSearch()`

### Fixed (bugs)
- `smlCh` initialized with wrong parameter name (`smcCh`)
- `` ` `` insertion marker moved into `SearchThread`

## [1.2.3] - 2000-08

### Initial release
- Java applet that fans search queries across multiple engines
- Concurrent scraping with result deduplication
- Asynchronous URL validation (`PingWatch`/`PingThread`)
- English and Chinese language support
- Visual J++ 6.0 build

---

## Version History

| Version | Date | Lines | Key Change |
|---------|------|-------|------------|
| 1.2.3 | 2000-08 | 1,512 | Initial release |
| 1.2.3 | 2001-12 | 1,529 | Last large version |
| 2.0.0.0 | 2002-01 | 906 | Rewrite and condensation |
| 2.0.0.0+patch | 2002-03 | 906 | Deadlock fix attempt |
