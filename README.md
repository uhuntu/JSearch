# JSearch

[![Tests](https://github.com/uhuntu/JSearch/actions/workflows/tests.yml/badge.svg)](https://github.com/uhuntu/JSearch/actions/workflows/tests.yml)
[![License: GPL-2.0](https://img.shields.io/badge/License-GPL--2.0-blue.svg)](LICENSE)
[![JDK 11+](https://img.shields.io/badge/JDK-11%2B-green.svg)](https://adoptium.net/)
[![Archive](https://img.shields.io/badge/Status-Archive-yellow.svg)](README.md)

> "JSearch - turns search Engines into FIND engines - Programming in JAVA"
> Copyright (C) 1999-2002 Hunt Lin \<huntlin@public.xm.fj.cn\>
> Licensed under the GNU General Public License, version 2 or later. See `Releases/COPYING.TXT`.

A Java **applet** that fans search queries out across multiple search engines
concurrently, scrapes the returned HTML for result blocks, and presents a single
de-duplicated result list with title, URL and preview. Built with Microsoft
Visual J++ 6.0 and documented against the Chinese national software
documentation standard **GB8567-88**.

This repository is an **archive**. The original program cannot be
built or run today (see [Why this cannot be revived](#why-this-cannot-be-revived)).
It is preserved and organized so the development history is legible.

---

## Read this first: encoding

**Almost every original file in this repository is encoded in GBK (GB2312),
not UTF-8.** The Java sources, the engine data files, and the HTML pages all
carry Chinese comments and labels in legacy encodings.

Opening them in a modern editor with the wrong encoding setting produces
mojibake, and — much worse — **saving** them will permanently corrupt them.

```sh
# read without converting
iconv -f GBK -t UTF-8 Sources/JSApplet.java | less

# in-place, keeping the original bytes
iconv -f GBK -t UTF-8 Sources/JSApplet.java -o /tmp/JSApplet.utf8.java
```

The files have deliberately **not** been converted. Converting would destroy
the artifact. Files added by this archival pass (this README, `.gitignore`)
are UTF-8 and use ASCII-only identifiers where practical.

---

## Generations

Four distinct snapshots of the program survive. They are kept under
`versions/` so they can be compared; the *current* state (`Sources/`,
`Releases/`, `ENGINES/`) is the final March 2002 revision.

| Path | Snapshot | Date | `JSApplet.java` | Version |
|---|---|---|---|---|
| `versions/2000-08/` | `JSApplet` | Aug 2000 | 1,512 lines | **1.2.3** |
| `versions/2001-12/JSApplet/` | `JSearch` | Dec 2001 | 1,529 lines | **1.2.3** |
| `versions/2002-01/` | `JSearch` | Jan 2002 | 906 lines | **2.0.0.0** |
| `Sources/` (live) | `JSearch` | Mar 2002 | 906 lines | **2.0.0.0 + patch** |

The lineage is **not** a steady climb. The program grew from 54 KB (2000) to a
peak of ~55 KB (Dec 2001), then was *rewritten and condensed* to ~28 KB in
January 2002, then patched again in March 2002. `Sources/JSApplet.java` is
byte-identical to `versions/2002-01/Sources/JSApplet.java`; only
`SearchThread.java` changed between January and March.

### These are four versions of **one** program, not two

This is the first thing the table above gets wrong if you read it naively. The
`JSApplet` directory from 2000 is **not a separate application** that happened to
share a name, and `JSearch` is not a superset that absorbed it. Both directories
are the same codebase at different points in its life.

The main class never got renamed:

```
versions/2000-08/JSApplet.java               public class JSApplet extends Applet
versions/2001-12/JSApplet/JSApplet.java      public class JSApplet extends Applet
versions/2002-01/Sources/JSApplet.java       public class JSApplet extends Applet
Sources/JSApplet.java                        public class JSApplet extends Applet
```

`JSApplet` is the name of the class, not of a component. It was chosen when the
code was purely an applet, and it survived the rename untouched. The product
itself was called **JSearch from the beginning** — the first comment line of the
2000 source already reads *"JSearch - turns search Engines into FIND engines"*,
with version `1.2.3`.

The shipped 2002 release therefore carries both names at once:

```html
<TITLE>JSearch 2.0.0.0 - [huntlin@public.xm.fj.cn]</TITLE>
<OBJECT CABBASE=JSearch.cab CODE=JSApplet.class WIDTH=758 HEIGHT=403>
```

`JSearch.cab`, `JSearch 2.0.0.0` — deploying a class file named `JSApplet.class`.
The rename changed the project, the cabinet, the title and the version string,
but not the class. So when this archive says "`JSApplet`", it means the 2000-era
directory name and the class name; when it says "`JSearch`", it means the project
and product name. Same program throughout.


`versions/2000-08/` was copied in from a separate repo (`uhuntu/JSApplet`), tagged
`v1.2.3-baseline`. The details, including why that commit is a 2026 snapshot and
not a development history, are in [PROVENANCE.md](PROVENANCE.md).

---

## Directory layout

```
README.md                    this file
.gitignore
Sources/                     v2.0.0.0 source, Mar 2002, + 2 race fixes  <-- the thing
  JSApplet.java                applet shell, UI, threading (as authored)
  SearchThread.java            the actual worker (+ the two fixes below)
  JSearch.sln / .sln / .vjp    Visual J++ 6.0 project files
  codebase.dat                 applet classpath config (dead path, see below)
Releases/                    release as shipped, JSearch.cab + JSearch.html + JSEngines.txt
ENGINES/                     hand-written engine notes + saved HTML snapshots
docs/                        GB8567-88 documentation set, Jan 2002 (8 .doc + txt/ text copies)
refs/                        GB8567-88 standard archive, 使用说明书, related material
artifacts/Classes/           compiled .class output (stale, see caveat below)
modern/                       compiling reference redesign + tests (not the original)
versions/                     the other three generations
  2000-08/                   v1.2.3 baseline
  2001-12/JSApplet/          last large version
  2002-01/                   v2.0.0.0 (Sources + Releases)
  2002-01-js.zip             the archive that `versions/2002-01/` was unpacked from
```

### Caveat: `artifacts/Classes/` is a mixed generation

Only 5 of the 24 compiled `.class` files match `versions/2000-08/`; the other
19 differ. It is an unclean incremental build, not a faithful compile of any
single source version. **Do not use it to infer what a given revision compiled
to.** The `.class` files in each `versions/` snapshot are more trustworthy.

---

## Analysis

The code-level findings live in [ANALYSIS.md](ANALYSIS.md):

- what changed from 1.2.3 to 2.0.0.0
- the "deadlock patch" that does not work (and the fix merged from `fix/showresult-locking`)
- the URL-keyed `Hashtable` that silently drops search engines
- what a better design would look like, implemented as a compiling reference in [modern/](modern/README.md)

---

## Landmines: hardcoded author paths

Every generation hardcodes an absolute path to the author's development
machine. **The applet loads its engine database from `currUrl`**, so a wrong
path means the app silently loads zero engines and shows an empty UI.

| Generation | Hardcoded value |
|---|---|
| 2000-08 | `file:/E:\Developing Software\JSApplet` (`CODEBASE.DAT`) |
| 2001-12 | `file:///D:/DevSofts/JSearch/Baks/JSApplet/` |
| 2002-01 | `file:///D:/DevSofts/JSearch/Releases/` |
| **live** | `file:///E:/DevSofts/JSearch/Releases/` |

Note the author developed on both a `D:` and an `E:` drive at different times,
so even the mangled path has moved around.

`getEngData()` reads `getParameter("currUrl") + "JSENGINES.TXT"`. That file
**does not exist in this working tree** — only inside
`Releases/JSEngines.txt` and the `versions/` snapshots. And
`Sources/codebase.dat` still points at `file:/D:\DevSofts\JSearch\Classes`.

---

## Why this cannot be revived

- **Applets are gone.** The Java Plug-in / NPAPI was removed from all major
  browsers between 2015 and 2021, and applet support was dropped from the JDK
  itself (deprecated in JDK 9, removed in JDK 11). `JSearch.cab` is an ActiveX
  CAB targeting Internet Explorer on Windows.
- **The scraped engines are gone.** `JSEngines.txt` points at
  `www.google.com/search?q=…` and `www1.baidu.com/baidu?word=…`. The scraper
  strategy is fixed-offset character matching (`stepOneChar()` shuffling a
  4-character sliding window), so it depends on exact HTML markup that has since
  changed many times over. Google and Baidu both block this pattern now.
- **There is no build system**, only a Visual J++ `.vjp` project file from 2001.
- **`Releases/JSearch.html` is not portable** — see landmines above.

Reviving it would mean rewriting the scraper *and* the UI while keeping none of
the original scraping logic, and having to re-derive block markers from scratch
against today's anti-bot measures.

---

## Provenance

Recovered and reorganized on 2026-09-28. Files were moved, not edited, with one
deliberate exception: `Sources/SearchThread.java` carries the two race fixes
described above. The original March 2002 bytes are still in git
(`bca88b3:Sources/SearchThread.java`, tag `pre-restructure-a664f23`). The full
record, including the backup checksum, is in [PROVENANCE.md](PROVENANCE.md).

---

## Further reading

- **[ANALYSIS.md](docs/ANALYSIS.md)** — Detailed design defect analysis and the deadlock patch
- **[ARCHITECTURE.md](docs/ARCHITECTURE.md)** — Visual comparison of original vs modern design
- **[modern/README.md](modern/README.md)** — Reference design: how it should have been built
- **[MIGRATION.md](MIGRATION.md)** — How to adapt the reference design for production
- **[FAQ.md](FAQ.md)** — Frequently asked questions
- **[QUICKSTART.md](QUICKSTART.md)** — Quick start guide for new contributors
- **[CONTRIBUTING.md](CONTRIBUTING.md)** — Guidelines for contributing to this archive
