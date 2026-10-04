# JSearch: analysis

Findings from reading the 1.2.3 -> 2.0.0.0 source. Split out of [README.md](../README.md). The reference redesign lives in [modern/](../modern/README.md).

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

## Further findings

Things a close reading of `Sources/JSApplet.java` and `Sources/SearchThread.java`
reveals beyond the two bugs above.

### `_stop` and `actualSearchAllowed` are not `volatile`

Both are `static` fields written by the UI thread (or by whichever search thread
happens to decrement last) and read by every other search thread. Neither is
declared `volatile`. Under the Java Memory Model a thread is free to cache the
value in a register and never see an update. On x86 this usually works in
practice because the hardware memory model is strong; on ARM or POWER it is
more likely to misbehave. The merged `searchCountLock` fix makes the *decrement*
atomic but does not address the visibility of reads elsewhere — `run()` checks
`JSApplet._stop` in the `for` condition and in `stepOneChar()`, both outside any
lock.

### Sleep as concurrency workaround

`SearchThread.run()` sleeps after each result:

```java
Thread.sleep(1000 - Integer.valueOf(JSApplet.smsCh.getSelectedItem()).intValue()*100);
```

The Chinese comment on this line reads *"让死锁有时间释放"* — "let the deadlock
have time to release." The "search max speed" control (`smsCh`) is therefore not
purely a bandwidth throttle: at its slowest setting the sleep is 900 ms per
result, at its fastest it is 0 ms. The author knew the locking was broken and
was using sleep duration as a mitigation strategy. The option label "Search max
speed" undersells what it was actually doing.

### `formatString()` is encoding-coupled

Column alignment in the results list uses `String.getBytes()` without specifying
an encoding, so it uses the platform default. For Chinese text in GBK each
character is 2 bytes and (in a monospaced CJK font) 2 columns wide, so the byte
length happens to equal the display width. On a UTF-8 platform the same Chinese
characters are 3 bytes each, and the columns would no longer line up. The entire
display layout is implicitly coupled to the platform encoding matching the font
encoding.

### Two `finalize()` overrides survived the rewrite

ANALYSIS.md's "what changed" section notes that ~20 `finalize()` overrides were
deleted in the 1.2.3 → 2.0 rewrite, each one forcing a full GC. Two remain:
`JSApplet.java:904` and `SearchThread.java:176`. The `SearchThread` one at least
tries to close `inURLStream` — but relying on GC for socket cleanup is
unreliable; the stream is closed in `run()` on the normal path and only reached
by `finalize()` if the thread is abandoned mid-search.

### `href=` matched by `ref=` — a substring accident

`analyseBlock()` scans forward looking for the four-character window `ref=` to
find the URL inside each result block. The HTML uses `href=`. This works because
`ref=` is a substring of `href=`: the sliding window matches `ref=` as it passes
over the `f` in `href`. Whether this was intentional or discovered by accident,
it is a hack that depends on the attribute name containing those four characters
in that order — an `<a name=...>` tag placed before the link would have produced
a false match.

---

## The `ENGINES/` snapshots: Java in December 2001

`ENGINES/` contains four saved HTML pages — the actual pages the scraper was
built to parse — captured on 2001-12-27. Reading them is a snapshot of the Java
ecosystem at peak enthusiasm.

The ten results on the captured page for "java" on Google (English), as Google
saw them. The pager marks page 2 as current, so these are results 11&ndash;20, not
the top ten:

| # | Site | What it was |
|---|---|---|
| 1 | sun.com/java | Sun Microsystems, the source |
| 2 | java.apache.org | Apache Java project |
| 3 | www.java-pro.com | Java Pro magazine |
| 4 | javascript.internet.com | False positive on "java" |
| 5 | microsoft.com/java | Microsoft's Java division |
| 6 | developer.java.sun.com | Java Developer Connection |
| 7 | javaarchives.com | Java archive |
| 8 | java.about.com | About.com Java section |
| 9 | ibiblio.org/javafaq | comp.lang.java FAQ |
| 10 | anfyteam.com | Java applets and screensavers |

Three of the ten point to sun.com subdomains. Microsoft had a Java page —
"Technologies for Java" — before the DOJ settlement and the eventual removal of
Microsoft's Java support from Windows. Anfy Team was selling Java applets and
screensavers. The page reports "about 23,100,000 results" and this is page 2
(results 11–20), with pagination going to page 11.

All four pages are encoded in GB2312 or GBK. The `google_en.html` page is
actually Google's Chinese-language interface (`hl=zh-CN`) serving results for an
English query — the search term "java" was being queried from a Chinese locale,
which is why the navigation labels ("下一頁", "結果") are in Chinese while the
results themselves are English.

The block markers in `JSEngines.txt` can be verified against these pages. For
Google English the markers are `<p><` (start) and `k - ` (end). Each result
block in the HTML begins with `<p><a href=...` and the metadata line ends with
something like `- 31k - `, so `k - ` is the trailing edge of the size/date
fragment. The scraper would extract: URL from after `ref=` (matched inside
`href=`), title from between the `<a>` tags, and preview from everything up to
`k - `.

That verification is now automated: `modern/` scrapes all four pages with the
markers read out of the shipped `Releases/JSEngines.txt`. Two things it turned up
are worth knowing before trusting the markers by eye:

- **`<p><` also matches the pager.** Google's pagination table opens with
  `<p><div class=n>`, so a page yields eleven results, the eleventh being the
  "上一页" link. The original had the same false positive.
- **Baidu's end marker `ble>` never occurs outside a tag.** All 24 occurrences in
  `baidu_cn.html` are the tail of `</table>`, so an implementation that steps
  from `<` to `>` never ends a Baidu block and the page collapses to one result.
  The original's window compared at every character, inside tags included, so it
  matched; a straightforward port does not.

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
it have been built?" The reference implementation is in `modern/`; the original code is
left as written, apart from the two race fixes described above.

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
