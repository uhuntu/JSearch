# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The 1.2.3 / 2.0.0.0 entries below are the **2002 product**. Unreleased entries
are archive housekeeping. This repo does not version a live search service.

## [Unreleased]

### Added
- `runner/`: a local runner that plays the part IE + the Java Plug-in used to
  play, so the untouched original `Sources/` applet runs on a modern JDK
  (tested on 17) — a `Frame`, the applet parameters, and an engine table that
  is the shipped `Releases/JSEngines.txt` converted to UTF-8 with a fourth,
  `LocalDemo` record served canned 2002-marker pages by `DemoServer.py`.
  `--go` / `--snap=FILE` support unattended verification; `first-light.png`
  is the first run's actual output (two results, dedup applied, preview
  rendered). This is the only path in the repo that executes the original
  artifact itself; `modern/` remains the redesign, not the original.

### Fixed
- The URL-keyed `Hashtable` analysis was wrong about the shipped data. Record
  1 of `Releases/JSEngines.txt` ends in a trailing space, so its key never
  collided with English Google's and nothing was ever dropped: running the
  original code loads three engines from three records, and the Chinese
  category lists both Google entries. `docs/ANALYSIS.md`, `README.md`,
  `README.zh-CN.md` and `modern/README.md` now state the defect accurately —
  an engine's identity held one stray byte away from silent data loss —
  instead of claiming the shipped file lost an engine.

### Changed
- `modern/WebServer.java` returned a captured 2001 page for **any** query.
  `fetchPageContent()` picked a fixture from the engine URL and never compared
  it against the query — while its own comment said the fixtures were for
  `"java"`. So a search for `applet` came back with ten links to sun.com and
  java.apache.org, and the UI presented them as an answer to `applet`, with
  every metric on screen describing the wrong question. A captured page now
  answers only the query it was captured against (`java` for
  `google_en.html` / `lycos_en.html`, `西二在线` for `google_cn.html` /
  `baidu_cn.html`); anything else falls through to the synthetic pages.
  Covered by a new test that fails against the old behaviour.

### Changed
- Stated the project identity: archive plus teaching sketch, not a search product
- `ROADMAP.md`, `CONTRIBUTING.md`, `MIGRATION.md`, and related docs now match that scope
- `scripts/verify-gbk.ps1` now detects conversion instead of assuming it cannot
  happen: it decodes strictly, where before it decoded with .NET's replacing
  fallback and so accepted a file that had already been rewritten as UTF-8.
  Its hardcoded `c:\Users\...` default has been replaced by the script's own
  repository root, and `docs/txts/` is globbed so the script carries no
  non-ASCII of its own.
- `WebServer` decodes `ENGINES/lycos_en.html` as ISO-8859-1, matching what
  `EncodingGuard` records for it, instead of GBK.
- Stale test counts in `modern/README.md`, `docs/ARCHITECTURE.md`, and
  `docs/COMPARISON.md` now say 44.

### Added
- `modern/src/test/java/jsearch/EncodingGuard.java` — same check without needing
  Windows: classifies every original file by strict GBK/UTF-8 decoding, lists
  files that changed encoding or went missing, and refuses anything of GBK
  creeping into `modern/` or the Markdown docs. Runs standalone and as four of
  the suite's tests.
- `.github/workflows/tests.yml` gained an `encoding` job that runs it on every
  push, so a converted artifact and a missing one both fail CI.
- `ENGINES/lycos_en.html` is recorded as ISO-8859-1 rather than GBK — the single
  original file that was never Chinese, previously implied to be GBK.

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
