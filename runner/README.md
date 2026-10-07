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
```

The `--go` run prints an evidence trail to stdout: how many engines the table
holds, which engines are listed and selected, when the search starts, how many
results land, and the preview text.

## The pieces

| File | Role |
|---|---|
| `JSearchRunner.java` | The IE/Plug-in substitute: `AppletStub` + `AppletContext`, the applet parameters (`currUrl`, `_readTxt`, options), a plain `Frame` window |
| `DemoServer.py` | A canned results page written to the 2002 scraper's marker format (`<p><` block start, `ref=` URL, `</a>` title, `</p>` block end); third block duplicates the first URL so dedup is visible |
| `JSENGINES.TXT` | The shipped `Releases/JSEngines.txt` converted to UTF-8, with a 4th record appended: `LocalDemo`, pointing at the demo server |
| `fontconfig.properties` | Composite-font mapping passed via `-Dsun.awt.fontconfig`; without it the AWT peers draw boxes for every Chinese string on Windows (see below) |
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
  printf '\nhttp://127.0.0.1:8901/\nLocalDemo / demo\nChinese\nhttp://127.0.0.1:8901/search?q=^&page=`0\n<p><\n</p>\n\n' >> JSENGINES.TXT
  ```

  Note the leading blank line in the appended record: the parser reads six
  lines plus a separator per record, and the shipped file does not end with
  one.

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
  2000 system font.

## What running it settled

**The shipped data never actually lost an engine.** `docs/ANALYSIS.md`
originally reported that the URL-keyed `Hashtable` in `getEngData()` silently
collapsed Chinese Google into English Google — three records in, two engines
out. Running the code proves otherwise: all four records load as four
engines, and the Chinese category lists both Google entries. The reason is a
single invisible byte: record 1's URL key is `http://www.google.com/ ` *with
a trailing space*, so the two keys never collided. The design defect is real
— a URL is not an identity, and the table is one stray byte, one trim, one
re-encode away from silent data loss — but the shipped file dodged it.
Analysis corrected accordingly; see `../docs/ANALYSIS.md`.

## Honest scope

The applet UI, threading, scraping and dedup are the original 2002 code, and
they work. What is dead is the world around them: the three historical
engines (2001-era Google/Baidu/Lycos URLs) no longer return pages this
scraper can read, so only `LocalDemo` produces results — selecting the others
shows the original's error handling in the `>>消息` pane, which is itself
authentic behaviour. Double-clicking a result hands the URL to `xdg-open`
(the browser-path option the applet already had).
