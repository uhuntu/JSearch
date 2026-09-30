# JSearch

> "JSearch - turns search Engines into FIND engines - Programming in JAVA"
> Copyright (C) 1999-2002 Hunt Lin \<huntlin@public.xm.fj.cn\>
> Licensed under the GNU General Public License, version 2 or later. See `Releases/COPYING.TXT`.

A Java **applet** that fans search queries out across multiple search engines
concurrently, scrapes the returned HTML for result blocks, and presents a single
de-duplicated result list with title, URL and preview. Built with Microsoft
Visual J++ 6.0 and documented against the Chinese national software
documentation standard **GB8567-88**.

This repository is an **archive**, not a working project. Nothing here can be
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

## What changed from 1.2.3 to 2.0.0.0

Rewriting 1,512 lines down to 906 — mostly good engineering, with a few real
deletions.

### Removed

- **`PingWatch` / `PingThread`** — the entire *asynchronous* URL-validation
  subsystem. In 1.2.3 each result was pinged on a second thread and marked
  `(+)` reachable / `(-)` unreachable / `(!)` timeout / `(?)` validating,
  written back into the results list.
- **`StopValid` button**, and the `Search timeout(s)` / `Validate timeout(s)`
  options that went with it.

Validation was replaced by a *synchronous* per-result TCP connect inside the
search thread itself (`SearchThread.validUrl()`). Simpler, no cross-thread
marker patching — but a blocking connect per result, which is why the ability
to cancel validating independently no longer existed. Note the **`Validate URL`
option survives in the UI** (`valurlCh`, default `No`) and is still read at
`SearchThread.java:79`.

### Added

- `smsCh` "Search max speed:" — caps simultaneous connections.
- `_readTxt` applet parameter. In 1.2.3, `init()` made two blocking
  `openStream()` calls to fetch `COPYING.TXT` / `CREDITS.TXT` purely to fill the
  ABOUT panel. In 2.0 that is gated behind a parameter that defaults to `No`,
  so startup no longer pays for it. A genuine win.
- Helpers `getTxtFile()`, `switchPSM()`, `switchEOA()`, `clearAll()`,
  `stopSearch()`, `newSearch()`.
- `actualSearchAllowed` now caps thread count at
  `min(selected engines, smcCh)`. Previously selecting 8 engines with a limit of
  4 created 8 threads but only drove 4, and the `srchThread.length` vs
  `threadCount` bookkeeping around that mismatch was the crash surface.

### Fixed

- `smlCh` was initialized with `getParameter("smcCh")` — a copy-paste error
  that populated "search max level" from the max-connections parameter.
- The `` ` `` insertion marker moved from `startSearch()` into
  `SearchThread` rather than being lost.

### Cleanup

- **~20 `protected void finalize() { super.finalize();
  System.gc(); System.runFinalization(); }` overrides deleted.** Each one forced
  a full GC on object collection. Probably the single biggest performance change
  in the rewrite.
- `Thread.stop()` eliminated in favour of a `_stop` boolean checked by the run
  loop.
- Error messages standardized to `"Exception: '...' in JSApplet.method()."`.
- The standalone `main()` frame wrapper was dropped.

---

## The deadlock patch does not work

This is the most important finding in the archive, and it is a bug.

In `versions/2002-01/Sources/SearchThread.java`, `showResult()` guarded its
critical section with **five nested `synchronized` blocks** on the shared
result collections. N search threads each acquiring several shared locks in
nested order is a textbook lock-ordering deadlock. The author knew — the
comment on `ResultLiIL` reads "这是最有可能发生死锁的地方" ("this is the most likely
place to deadlock").

The March 2002 patch changed it to:

```java
synchronized void showResult() {
//  synchronized (JSApplet.resultTable) { ... all five inner blocks commented out ... }
```

**`synchronized` on an instance method locks on `this`.** Each `SearchThread` is
a *separate instance*, so thread 0 and thread 1 contend for *different*
monitors. The lock provides **zero mutual exclusion between search threads**,
and the five inner locks — the only ones that guarded shared state — are gone.

The deadlock is resolved only in the weakest possible sense: there is no
locking left to deadlock on. The patch looks like a fix and is not one.

What actually remains:

- **TOCTOU** between `resultTable.containsKey()` and `resultTable.put()` — two
  engines returning the same URL can both insert, producing a duplicate row in
  `resultLi` and a duplicate element in `resultIndex`.
- **Lost update** on `totalNumLa.setText(getText() + 1)` — the "Total:" count
  can drift below the true result count.
- **Non-atomic** `--JSApplet.actualSearchAllowed == 0` — the last thread's
  `buttonStatus(2)` may never fire, leaving the UI stuck in search mode.
- **AWT calls off the event dispatch thread** — `resultLi.add()`,
  `statusLi.replaceItem()` and `setText()` from worker threads. Illegal in AWT;
  never addressed in any revision.

`Hashtable` and `Vector` methods are internally synchronized in pre-JDK5 Java,
so container *integrity* survives; the damage is logical races. A correct fix
would be a single static lock object covering the check-then-act sequence *and*
the AWT updates, the latter released to `EventQueue.invokeLater` (AWT, not
Swing — these are `java.awt` components, so `SwingUtilities` is the wrong API).

**Fixed, and merged.** Branch `fix/showresult-locking` (heads `9d3732d`, now
merged into `main` via `6672db6`) repairs the result-collection races: a shared
static `resultLock` replacing the per-instance lock that never contended
between threads, and a second static `searchCountLock` making the
`actualSearchAllowed` decrement atomic. Both critical sections cover the
check-then-act *and* the AWT updates together. Verified by compiling on JDK 8
(`-encoding GBK`) and reading the resulting bytecode — `getstatic resultLock /
monitorenter` before the `containsKey` probe, and `monitorenter` around the
decrement with a `monitorexit` on the exception path.

The merge is a no-ff merge, so the two fixes stay a named, reviewable unit in
history rather than being flattened into `main`'s line. It was merged because
two verified fixes are worth more than a symbolically pure snapshot, and
because nothing was lost by doing so — see Provenance for where the original
bytes live.

The off-EDT AWT calls are **not** fixed: the lock makes those updates safe from
each other, not from AWT's own single-thread rule, and moving them to
`invokeLater` would change the order results appear on screen.

---

## A design defect, independent of the races

This one is more clearly broken than anything above, and it is a design error
rather than a coding slip. **JSearch silently loses whole search engines.**

`getEngData()` builds its engine table by keying a `Hashtable` on the **URL**:

```java
engDataHt.put(engDataHtHead, engDataHtBody);   // engDataHtHead is the URL line
```

A URL is not an identifier. The shipped `Releases/JSEngines.txt` defines three
engines, and two of them share their URL:

| Key | Name | Category |
|---|---|---|
| `http://www.google.com/` | Google / Google | English |
| `http://www1.baidu.com/` | Baidu / 百度 | Chinese |
| **`http://www.google.com/`** | **GB_Chinese Google** | **Chinese** |

Parsing that file the way the code does — 6 fields plus a blank line per record,
last write wins — **three records in, two engines out.** The Chinese Google
entry is overwritten by the English one and disappears from the category list
without a word to the user. Anyone who ever wondered why "Chinese" showed only
Baidu, this is why: the second engine was never loaded.

The fix is a data model, not a patch: identity belongs to an object
(`name` + `category`), carried in a `List<Engine>`, with the format itself
rejecting or namespacing duplicate keys. No amount of care in `getEngData()` can
recover an identity that was thrown away by the key choice.

---

## What a better design would look like

Recorded because a reader of this archive will reasonably ask "so how *should*
it have been built?" None of it is implemented here — this is documentation, not
a rewrite. The 2002 source is left exactly as the author wrote it.

**Component separation.** `JSApplet` is ~900 lines that simultaneously lay out
the UI, fetch over HTTP, own the threading, hold the result state, switch
languages, and launch a browser. Split along those axes:

```
Engine          (id, name, Category, urlTemplate, blockStart, blockEnd)
EngineRepository  load List<Engine> from a versioned, duplicate-checked file
SearchResult    (engine, title, url, preview, rank)
BlockScraper    interface  -> HtmlBlockScraper, the existing char-window
                             algorithm extracted and made testable
ResultCollector thread-safe, dedups by url, owns the count, notifies listeners
SearchService   ExecutorService fixed pool; submit(Callable); cancel(true)
SearchViewModel presenter logic, EDT-safe
View            layout managers instead of absolute coordinates
Bundle          ResourceBundle instead of a changeLanguage switch
Launcher        Desktop.browse() instead of Runtime.exec(path + " " + url)
```

**Concurrency solved instead of hand-rolled.** The current design runs one
`Thread` per engine, tracks them with a `static int actualSearchAllowed` you
decrement yourself, and polls a `_stop` boolean once per character read. An
`ExecutorService.newFixedThreadPool(maxConnections)` deletes the counter and the
flag, and `cancel(true)` interrupts the blocked socket read rather than relying
on a flag that gets checked between characters. This is what would have made the
two races above unrepresentable rather than merely fixable.

**One truth for results.** Today the same fact lives in four places: a
`Hashtable` for detail, a `Vector` for URLs, a `List` for the visible rows, and
a `Label` for the count. A single `ResultCollector` that owns its lock, dedups,
counts, and publishes changes to a listener is both less code and the only place
a UI update can originate.

**Testability.** `BlockScraper` as an interface means the scraping algorithm can
be exercised against saved HTML fixtures — `ENGINES/*.html` are exactly that —
without a browser, a network, or an applet.

**The honest caveat, restated because it bounds all of the above:** applets are
gone from browsers and from the JDK, and every engine this scraped is dead or
blocking. A redesign is worth doing as a study or a port, not as a revival.

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

Recovered and reorganized on 2026-09-28. Files were moved, never edited, with one
deliberate exception: `Sources/SearchThread.java` carries the two race fixes
described above. The original March 2002 bytes are still in git
(`bca88b3:Sources/SearchThread.java`, tag `pre-restructure-a664f23`). The full
record, including the backup checksum, is in [PROVENANCE.md](PROVENANCE.md).
