# runner — run the untouched 2002 applet

`modern/` answers "how *should* it have been built?" This directory answers a
different question: *does the original artifact itself still run?* It does.
The two classes in `../Sources/` are compiled here byte-for-byte as shipped —
same logic, same AWT UI, same 4-character sliding-window scraper — and driven
by a small shim that plays the part Internet Explorer and the Java Plug-in
used to play.

`first-light.png` is the first run's actual output: the original Chinese UI,
two results scraped from a live HTTP fetch, the URL-duplicated third block
dropped by the original dedup, and the preview pane rendering.

## Quick start

```sh
cd runner
./run.sh          # opens the applet window; type a query, press 开始搜索 or Enter
```

On Windows you have three ways in: `./run.sh` from Git Bash; `bash ./run.sh`
straight from PowerShell (Git's bash is first on PATH); or `.\run.ps1` from
PowerShell, which needs no Git Bash at all. Note that plain `.\run.sh` in
PowerShell silently does nothing — PowerShell does not execute `.sh`
scripts. `.\run.ps1` needs script execution to be allowed on your machine;
if it is blocked, run it once as
`powershell -ExecutionPolicy Bypass -File run.ps1`, or set
`Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` (per-user, one time).

`run.sh` compiles into `classes/` on first run (JDK 17 tested; any JDK whose
`java.applet` still exists works — the API is deprecated for removal, so very
new JDKs may eventually break this), starts `DemoServer.py` on
`127.0.0.1:8901` if it is not already running, and launches the applet.

Flags, for unattended verification:

```sh
./run.sh --go              # auto-type "JSearch" and start a search after 1s
./run.sh --go --snap=x.png # additionally save a PNG of the applet after 6s
./run.sh --go --engine=SearXNG --levels=2
                           # search the bridge engine, two pages deep
```

`--engine=` takes a comma-separated list of engine-name prefixes and selects
exactly those, deselecting the rest; `--levels=` sets the applet's own `smlCh`
(search level) choice. Both are things a user can do by hand in the window —
the flags only make a run reproducible. The default is `LocalDemo`, the one
engine that answers out of the box.

The `--go` run prints an evidence trail to stdout: how many engines the table
holds, which engines are listed and selected, when the search starts, how many
results land, and the preview text.

## Searching the live web (optional)

The fifth engine record, `SearXNG / live web aggregation`, points at
`SearxngBridge.py` on `127.0.0.1:8902` — a small translator that asks a local
SearXNG (JSON API on `http://127.0.0.1:8888`) for results and re-emits them
in the 2002 dialect the scraper reads. Start the bridge and the applet
together with:

```sh
./run-searxng.sh        # starts the bridge if needed, then the applet as usual
```

Select `SearXNG / live web aggregation` in the engine list and search: the
original sliding-window scraper then parses live web results, unchanged.
`python3` and a local SearXNG with its JSON API enabled are the only
requirements (on Windows, run the script from Git Bash). Without the bridge
the record simply fails like the historical engines do — selecting it shows
the original's own error handling, which is authentic behaviour.

Two details of the 2002 dialect decide whether a translated page is read
correctly, and both are invisible until they go wrong:

- **The level lives in the tens digit.** The shipped templates are Google's
  `start=`0` and Baidu's `pn=`0` — 0-based result offsets counting by ten — so
  the applet asks for `page=00, 10, 20` as the level goes 0, 1, 2. SearXNG
  numbers its pages from one, so the bridge divides the offset back out.
  Handing it the raw value asks for page 11 when level 1 meant page 2, which
  returns nothing and says nothing.
- **A page needs something after its last block.** After `analyseBlock()` the
  scraper steps one character and breaks when that fails, on the assumption
  that a page's real end marker follows its last result — so a block whose
  `</p>` lands on end-of-stream is dropped. `DemoServer.py` ends its page with
  `</body></html>` for exactly this reason, and the bridge emits the same
  terminator. Without it the last result of every page is silently lost.

Set the level in the window, or `--levels=2` on the command line, to watch the
applet's own paging loop run against the bridge.

## Verifying the live path without a network

`verify.sh` can exercise the whole bridge chain — applet, bridge, translated
page, 2002 scraper — with no SearXNG and no internet, by putting a fixture
SearXNG (`StubSearxng.py`) behind a bridge and pointing a temporary copy of the
engine table at it:

```sh
./verify.sh --live        # the applet searches the fixture SearXNG alone
./verify.sh --combined    # the same, plus the demo engine, so both engines
                          # hand the original one URL in common and its
                          # cross-engine dedup is what is on trial
```

The fixture is page-aware on purpose: page one carries three results, page two
two more, so a two-level run has to reach the second page for the count to come
out right. Ports are picked free and the copy is temporary, so neither mode
disturbs a bridge already running for real.

## CI checks that this still works

`first-light.png` is a snapshot of one run on one day. The `runner` job in
`../.github/workflows/tests.yml` re-establishes it on every push, three ways:

```sh
./run.sh --go > runner-ci.log   # then:  ./verify.sh runner-ci.log
./verify.sh --live              # the bridge chain, against a fixture SearXNG
./verify.sh --combined          # the bridge chain plus the demo engine
```

Each runs the untouched applet headless under `xvfb` — `--live` and
`--combined` fall back to `xvfb-run` themselves when there is no `DISPLAY`, and
say so plainly when neither a display nor `xvfb-run` is available.
The applet's window never closes, so the run is capped and `timeout`'s exit
124 is expected — the log is the artefact, and the assertions are the gate.

What the assertions protect, and why each is worth a line:

- **5 of 5 engine records load.** The URL-key collision this archive's analysis
  used to report never actually happened, because record 1's key carries a
  trailing space. Trim it and this is the assertion that fails.
- **4 engines listed in the Chinese category, and English Google not among
  them.** The two Google records are in different categories, so the Chinese
  list never held both. This one exists because the prose repeatedly said it
  did. `SearXNG`, the fifth record, is listed too.
- **`LocalDemo` is the selected engine** — the deterministic setup found the
  only engine that can still answer.
- **2 results from 3 scraped blocks** — the original 4-character sliding-window
  scraper and its dedup, unchanged.
- **A preview read back** out of the applet's own result table.

The two fixture modes add what a single demo page cannot reach:

- **The applet's own level loop.** Nothing else in the repository makes the
  original fetch a second page, and the two ways that page can be mistranslated
  — the tens-digit offset and the missing page terminator — both fail quietly.
  Each has its own assertion, and reverting either fix makes them fail.
- **Cross-engine dedup.** `--combined` hands the original two engines with one
  URL in common: the demo engine contributes two results, the bridge scrapes
  five and adds four, and the shared URL appears exactly once. The per-engine
  status lines are what make the arithmetic visible.

All of them match ASCII the runner prints verbatim, so the check never depends
on the Chinese strings decoding a particular way in the log. The demo server's
own access line is deliberately *not* asserted: `run.sh` reuses an already
listening 8901, so that line only appears in the log of whichever run happened
to start the server.

```sh
./run.sh --go > run.log 2>&1     # or: .\run.ps1 --go > run.log 2>&1
./verify.sh run.log              # 12 checks, exit 0 or 1
```

On Linux, install a CJK font first (`fonts-noto-cjk`) or AWT will draw every
label as a box — the run still passes, it just is not proving much.

## The pieces

| File | Role |
|---|---|
| `JSearchRunner.java` | The IE/Plug-in substitute: `AppletStub` + `AppletContext`, the applet parameters (`currUrl`, `_readTxt`, options), a plain `Frame` window. Also the `--engine`/`--levels` flags that make an unattended run deterministic |
| `JSENGINES.TXT` | The shipped `Releases/JSEngines.txt` converted to UTF-8, with two records appended: `LocalDemo` (the demo server) and `SearXNG` (the bridge) |
| `DemoServer.py` | A canned results page written to the 2002 scraper's marker format (`<p><` block start, `ref=` URL, `</a>` title, `</p>` block end); third block duplicates the first URL so dedup is visible |
| `SearxngBridge.py` | Opt-in translator: SearXNG's JSON API in, 2002-dialect result pages out, so the untouched scraper can read the live web. Started by `run-searxng.sh` |
| `StubSearxng.py` | A fixture SearXNG: the same JSON shape, page-aware, so `verify.sh --live`/`--combined` exercise the bridge with no network |
| `run-searxng.sh` | Starts the bridge if it is not listening, then launches the applet exactly as `run.sh` does |
| `fontconfig.properties` | Composite-font mapping passed via `-Dsun.awt.fontconfig`; without it the AWT peers draw boxes for every Chinese string on Windows (see below). Windows only — see the launch-flag note |
| `verify.sh` | Asserts the evidence trail. Three modes: a log to check, `--live`, and `--combined` — the last two run the applet against the fixture themselves |
| `verify-fontconfig.sh` | Asserts that the font shim is applied on Windows and not elsewhere, using a faked `uname` so both branches are checkable from either platform |
| `first-light.png` | Output of the first verified run |

Three things in this directory deserve explanation, because all are shims for
the environment, not changes to the applet:

- **`keepAuthoredGeometry()`** — the 2002 VMs laid the applet out from the
  `setBounds()` calls alone. Modern AWT instead runs the `BorderLayout`
  assigned at the end of `controlSetting()` on first validate, which stacks
  every component full-size. The shim records the authored bounds and drops
  the layout managers (keeping the intentional `CardLayout` on the tab panel)
  before the window is shown.
- **Engine-table charset** — `getEngData()` reads with the platform default
  charset, so the shipped GBK engine file would load as mojibake here. The
  served copy is converted to UTF-8 instead; the original bytes are untouched
  one directory up. Regenerate it with:

  ```sh
  iconv -f GBK -t UTF-8 ../Releases/JSEngines.txt > JSENGINES.TXT
  printf '\nhttp://localhost:8901/\nLocalDemo / 本地演示引擎\nChinese\nhttp://localhost:8901/search?q=^&page=`0\n<p><\n</p>\n\n' >> JSENGINES.TXT
  printf 'http://127.0.0.1:8902/\nSearXNG / live web aggregation\nChinese\nhttp://127.0.0.1:8902/search?q=^&page=`0\n<p><\n</p>\n' >> JSENGINES.TXT
  ```

  Note the leading blank line in the first appended record: the parser reads
  six lines plus a separator per record, and the shipped file does not end
  with one. The second append needs none — the first ends with a separator.

- **Windows launch flags (in `run.sh`, mirrored by `run.ps1`)** — two, both
  tested on JDK 11 on a
  Chinese-locale Windows 11 box. `-Dfile.encoding=UTF-8`: the applet reads
  `JSENGINES.TXT` and the result pages with the platform charset, which is
  GBK here, so every Chinese string decodes as mojibake without it. (JDK 18+
  defaults to UTF-8 and no longer needs it.) `-Dsun.awt.fontconfig=
  fontconfig.properties`: the AWT heavyweight peers build their GDI font
  from the logical font's alphabetic component, which on this stack carries
  no CJK glyphs — labels, list items, buttons and choices draw boxes even
  when the strings are correct (only the preview pane's edit control
  font-links on its own). The bundled file re-points that component at a
  CJK face, restoring the rendering the applet got from a Chinese Windows
  2000 system font. That flag is applied **on Windows only**, and
  `verify-fontconfig.sh` is what keeps it that way: the bundled file names
  Windows faces and works around a GDI-specific defect, so passing it to a
  Linux or macOS JVM would replace that platform's own font configuration with
  a mapping it cannot satisfy. Everywhere else AWT keeps its built-in
  behaviour.

## What running it settled

**The shipped data never actually lost an engine.** `docs/ANALYSIS.md`
originally reported that the URL-keyed `Hashtable` in `getEngData()` silently
collapsed Chinese Google into English Google — three records in, two engines
out. Running the code proves otherwise: all five records load as five engines.
They also come back as four in the Chinese category (`GB_Chinese Google`,
`Baidu`, `LocalDemo`, `SearXNG`), because the two Google records are in
different categories — `GB_Chinese Google` is Chinese, plain `Google` is
English — so the Chinese list never held both in the first place.
The reason the keys stayed distinct is a
single invisible byte: record 1's URL key is `http://www.google.com/ ` *with
a trailing space*, so the two keys never collided. The design defect is real
— a URL is not an identity, and the table is one stray byte, one trim, one
re-encode away from silent data loss — but the shipped file dodged it.
Analysis corrected accordingly; see `../docs/ANALYSIS.md`.

## Honest scope

The applet UI, threading, scraping and dedup are the original 2002 code, and
they work. What is dead is the world around them: the three historical
engines (2001-era Google/Baidu/Lycos URLs) no longer return pages this
scraper can read, so out of the box only `LocalDemo` produces results —
selecting the others shows the original's error handling in the `>>消息`
pane, which is itself authentic behaviour. The one live path is opt-in:
`./run-searxng.sh` starts `SearxngBridge.py`, and the `SearXNG` record then
answers from the live web through it. Double-clicking a result hands the URL
to `xdg-open` (the browser-path option the applet already had).
