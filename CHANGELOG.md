# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The 1.2.3 / 2.0.0.0 entries below are the **2002 product**. Unreleased entries
are archive housekeeping. This repo does not version a live search service.

## [Unreleased]

### Added
- `modern/`: a fourth `WebServer` mode, `live`, that runs the same `Engine`
  records, `SearchService` fan-out and `ResultCollector` dedup against four
  public APIs that need no key — Wikipedia, Stack Exchange, Hacker News, and a
  local SearXNG (`-Djsearch.searxng=`, default `127.0.0.1:8888`). `JsonApiScraper`
  grew per-field candidate keys (Hacker News spells a text post's title
  `story_title` and its preview `story_text`) and a `{field}` URL template for
  the two APIs that carry no URL of their own. Each engine now reports how many
  results it found, how long it took, and why it returned nothing, so a failing
  engine is data rather than silence. Verified against the live APIs: 44
  results, 42 after dedup, 1.3 s for "java applet", all four answering.
  Marginalia is deliberately not in the set although it publishes a no-key JSON
  API: it takes the query as a *path* segment, and the `^` slot encodes a query
  for a query string, where a space becomes `+`. In a path that is a literal
  plus, so "java applet" reaches it as a search for "java+applet" — zero
  results, no error, a silent wrong answer. Encoding the slot for paths would
  change every archive-mode URL, so Marginalia is reached through the local
  SearXNG instance instead, which aggregates it and spells the request
  correctly.
- `modern/src/main/java/jsearch/RelevanceCheck.java` — a response that parses
  can still be about nothing. Bing, asked from a server IP, stops failing and
  serves popular pages unrelated to the query; a response in which no hit
  mentions any significant query term is dropped rather than shown as results.
  Bing is out of the live set for that and because its HTML no longer matches
  the archive's markers, but the check is here for whatever is added next.
- `runner/`: a local runner that plays the part IE + the Java Plug-in used to
  play, so the untouched original `Sources/` applet runs on a modern JDK
  (tested on 17) — a `Frame`, the applet parameters, and an engine table that
  is the shipped `Releases/JSEngines.txt` converted to UTF-8 with two records
  appended: `LocalDemo`, served canned 2002-marker pages by `DemoServer.py`,
  and `SearXNG`, served by `SearxngBridge.py` (opt-in via `run-searxng.sh`),
  which translates a local SearXNG's JSON API into the 2002 dialect so the
  untouched scraper reads live web results. `--go` / `--snap=FILE` support
  unattended verification; `first-light.png` is the first run's actual output
  (two results, dedup applied, preview rendered). This is the only path in
  the repo that executes the original artifact itself; `modern/` remains the
  redesign, not the original.
- A `runner` CI job, so that claim is checked on every push instead of resting
  on one screenshot. It runs the untouched applet headless under `xvfb` with
  `run.sh --go`, then asserts the evidence trail the runner prints: 5 of 5
  engine records loaded (the URL-key collision that the old analysis reported
  never actually happened), 4 of 5 listed in the Chinese category with English
  Google absent and the `SearXNG` record present, the demo engine selected,
  2 results out of 3 scraped blocks (the original dedup working), and a
  preview read back out of the applet's own result table.
  `runner/verify.sh` holds the assertions and can be pointed at any log, so
  the same check runs locally; all of them are on ASCII the runner prints
  verbatim, so they do not depend on the Chinese text decoding a particular
  way. `runner/verify-fontconfig.sh` covers the platform decision in `run.sh`
  described below.
- `docs/GENERATIONS.md`: the four snapshots compared by measurement — line and
  byte counts, the CR-stripped 72-line diff that is the whole 2000-08 →
  2001-12 delta (a misspelled override fixed, a wrong parameter name, one
  statement reorder, otherwise whitespace), the grep counts that locate the
  2002-01 rewrite (23 `finalize()` declarations → 1, `Thread.stop()` 14 → 0,
  the PingThread/PingWatch subsystem 47 references → 0, `synchronized` 19 → 0
  in `JSApplet.java` as the locking moved into `SearchThread.java`), and the
  nine hunks that are the entire 2002-01 → 2002-03 delta in
  `SearchThread.java` (two shared static locks, the atomic
  decrement-and-check, five nested `synchronized` blocks collapsed into one
  `synchronized (resultLock)`). Every figure has a command that prints it.
- `runner/SearxngBridge.py` + `runner/run-searxng.sh`: an opt-in engine that
  answers from the live web, without touching the original code. The bridge
  asks a local SearXNG's JSON API and re-emits the results in the 2002 marker
  dialect (`<p><a ref=URL>TITLE</a><br>PREVIEW</p>`), so the untouched
  4-character sliding-window scraper parses them unchanged;
  `run-searxng.sh` starts it and launches the applet. The fifth engine record
  (`SearXNG / live web aggregation`, Chinese) ships in `JSENGINES.TXT`, and
  `verify.sh`, the runner docs and the READMEs' "cannot be revived" sections
  now expect five records and four in the Chinese category. Without the
  bridge the record fails exactly like the historical engines do, which is
  the original's own error handling on display.
- `runner/StubSearxng.py` and `verify.sh --live` / `--combined`: the bridge
  chain is verifiable with no SearXNG and no network. The fixture serves
  SearXNG's JSON shape and is page-aware — three results on page one, two more
  on page two — a bridge is started in front of it, and the applet runs against
  a temporary copy of the engine table whose SearXNG record names that bridge,
  so nothing disturbs a bridge already running for real. `--live` searches the
  bridge engine alone; `--combined` adds the demo engine, which hands the
  original two engines with one URL in common and puts its cross-engine dedup
  on trial. These are the only checks that reach the applet's own level
  (paging) loop, which a single-page search never touches. Ports are picked
  free and the temporary files are cleaned up.
- `run.sh --engine=` / `--levels=`: an unattended run can name the engines to
  select (comma-separated name prefixes, default `LocalDemo`) and the applet's
  own `smlCh` level, so a multi-engine, multi-page run is reproducible. The
  runner prints each result line and each engine's status line — the only way
  to see the original's fan-out, and the only way to tell which engine a shared
  URL was credited to.

### Fixed
- `modern/`'s `HttpPageFetcher` sent no `User-Agent`, so it went out as
  `Java/17` and Wikipedia answered 403 to every live search. It now identifies
  itself, and `withHeader` *replaces* a default per URL prefix instead of
  appending to it — `HttpRequest.Builder.header()` adds a second line rather
  than overwriting the first, and two `User-Agent` values on one request is a
  request an API may refuse.
- `modern/`'s URL template filled its `{field}` placeholders with form
  encoding, where a space becomes `+`. In a URL path a `+` is a literal plus,
  so a Wikipedia article titled "Java virtual machine" was linked as
  `Java+virtual+machine`, which Wikipedia answers with 404. Template values are
  now percent-encoded. The live run is what caught it: the links looked right
  and none of them opened.
- `modern/`'s README said mixing JSON shapes in one `SearchService` "is not
  built"; the live mode builds exactly that (`LiveEngines.dispatch()` routes
  each engine's response to the parser for its shape), so the note and the
  stale test counts (44) in `modern/README.md`, `docs/ARCHITECTURE.md`,
  `docs/COMPARISON.md` and `README.zh-CN.md` now say 57.
- The URL-keyed `Hashtable` analysis was wrong about the shipped data. Record
  1 of `Releases/JSEngines.txt` ends in a trailing space, so its key never
  collided with English Google's and nothing was ever dropped: running the
  original code loads three engines from three records. `docs/ANALYSIS.md`,
  `README.md`, `README.zh-CN.md` and `modern/README.md` now state the defect
  accurately — an engine's identity held one stray byte away from silent data
  loss — instead of claiming the shipped file lost an engine.
- That correction went one step further than the evidence supports, and
  `FAQ.md` was missed entirely. Three files claimed "the Chinese category lists
  both Google entries"; it never did. `GB_Chinese Google` is Chinese and plain
  `Google` is English, so the two records land in different categories — which
  the table ten lines above the claim in `docs/ANALYSIS.md` already showed.
  Selecting Chinese lists 2 of the 3 shipped engines (4 of 5 once `LocalDemo`
  and the `SearXNG` record are appended), verified against the applet's own
  output. `FAQ.md` still
  carried the original, disproven "silently dropped due to URL key collision",
  even though the entry above lists the files that had been corrected.
- `runner/run.sh` passed `-Dsun.awt.fontconfig=fontconfig.properties` on every
  platform, though that file names Windows CJK faces and exists to work around
  a GDI-specific font defect. On Linux or macOS it replaced the platform's own
  font configuration with a mapping it cannot satisfy. The flag is now applied
  on Windows only.
- `README.zh-CN.md`'s "why this cannot be revived" list named three of the
  four blockers, missing that `Releases/JSearch.html` is not portable; it now
  matches the English README.
- The modern Web UI still taught the disproven version of the engine-table
  story. The "1999 Buggy" alert, the compare API's `defectExplanation`, and
  the side-by-side "vanished engine" card all said English and Chinese Google
  shared `http://www.google.com/` and the second entry silently discarded the
  first — the claim the runner disproved and three correction rounds had fixed
  everywhere except the UI. The texts now state the corrected history and
  describe the emulation as what it is: normalizing the URL keys, which is
  exactly what one trim or re-encode would have done. The compare test asserts
  the explanation carries the trailing-space fact and no longer the disproven
  phrasing.
- `QUICKSTART.md` still opened with "the 1999–2002 applet itself cannot run" —
  written before `runner/` existed, and it never mentioned the Web UI. It now
  documents both paths: `build.sh -w` / `build.ps1 -Web` for the Web UI and
  `run.sh` for the original applet, and lists `runner/` in the structure.
- `SearxngBridge.py` mistranslated the applet's page number. The 2002 templates
  carry the level in the tens digit — the shipped records are Google's
  `start=`0` and Baidu's `pn=`0`, both 0-based result offsets counting by ten —
  so the applet asks for `page=00, 10, 20` as the level goes 0, 1, 2. The
  bridge handed that value straight to SearXNG's 1-based `pageno`, so level 1
  asked for page 11 instead of page 2: no results, and no error. It divides the
  offset back out now.
- `SearxngBridge.py` also dropped the last result of every page. After
  `analyseBlock()` the scraper steps one character and breaks when that fails,
  on the assumption that a page's real end marker follows its last result, so a
  block whose `</p>` lands on end-of-stream is never shown. `DemoServer.py`
  ends its page with `</body></html>` for exactly this reason; the bridge
  emitted a bare list of blocks. It emits the terminator now.
- `run.sh` compiled only when `classes/JSearchRunner.class` was missing, so a
  failed edit left the previous class in place and the script ran code that no
  longer existed — a verification run passing against a build that never
  happened. It now recompiles when a source is newer than its class, which
  also catches an edit or a re-encode under `Sources/`.
- The runner reported the result count as soon as it held still for two polls.
  The scraper pauses between blocks and between levels, so that count was
  mid-scrape: the first two-level bridge run reported 2 results while the level
  loop was still fetching. It now waits for the applet's own completion signal
  — `SearchThread` sets `_stop` when the last engine's thread exits, and every
  `showResult()` has run by then — and reports a finished-but-empty search as a
  count of 0 rather than as silence.

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
- Stale test counts in `modern/README.md`, `docs/ARCHITECTURE.md`,
  `docs/COMPARISON.md`, and `README.zh-CN.md` now say 44.

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
