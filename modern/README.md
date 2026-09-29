# JSearch — reference design

A small, compiling sketch of how JSearch should have been put together. **This is
not a revival and not a port of the original.** Nothing here talks to a network, a
browser, or an applet; the fetcher serves canned HTML so the whole pipeline runs
on any JVM.

The original source, in all four generations, is untouched one directory up. This
exists to answer the question the archive raises: *how should it have been
built?*

## Build and run

Requires JDK 11 or later. UTF-8 throughout — the original's GBK is not carried
over.

```sh
javac -encoding UTF-8 -d out $(find src/main/java -name '*.java')
java  -Dfile.encoding=UTF-8 -cp out jsearch.Demo
```

## What it demonstrates

The three properties the original design got wrong:

**1. Two engines may share a URL without one destroying the other.**
`Engine`'s identity is `(name, category)` and deliberately excludes the URL. The
shipped `JSEngines.txt` contains two engines both keyed
`http://www.google.com/` — Chinese Google and English Google — and the original's
`Hashtable`-keyed-on-URL collapsed them to one, silently dropping Chinese Google.
Parsing the same file here yields both.

**2. A genuinely duplicated engine is rejected, not overwritten.**
Identity is checked before insertion, so a real duplicate is a loud error naming
both line numbers. Silent last-write-wins is what made the original bug invisible.

**3. Deduplication and counting hold under concurrency.**
`ResultCollector` does the check-then-act inside a single lock, so two engines
returning the same URL cannot both insert it. Four concurrent tasks over three
result blocks with one duplicated URL record exactly two results.

## What replaced what

| Original | Here |
|---|---|
| `Hashtable` keyed on URL, plus `Hashtable` detail + `Vector` URLs + `List` rows + `Label` count | one `ResultCollector` with one lock |
| `Thread` per engine, `static int actualSearchAllowed`, polled `static boolean _stop` | `ExecutorService` fixed pool, `Future`, `cancel(true)` |
| mutable `ResultsDetails` shared across threads, defensively copied at the last moment | immutable `SearchResult` |
| scraping inline in the worker, mutating statics | `BlockScraper` interface, `HtmlBlockScraper` impl, pure `String` → `List<SearchResult>` |
| `changeLanguage(int)` switch with hardcoded strings | *(not built)* — `ResourceBundle` is the obvious next step |
| `setBounds(x,y,w,h)` everywhere | *(not built)* — layout managers |
| `Runtime.exec(browser + " " + url)` | *(not built)* — `Desktop.browse()` |

`cancel(true)` interrupts a thread blocked on a socket read, where the original's
`_stop` flag was only noticed between characters. That is the difference between
a cancel that works and one that waits for the network.

## Deliberately preserved

The scraping algorithm is the original's: scan forward, compare against block
markers, read the URL after `ref=`, the title up to `</a>`, the preview up to the
end marker, dropping anything between `<` and `>`. The exercise is the surrounding
design, not a better parser.

One thing *was* changed in the port, and it is a real fix rather than a
simplification: the original compared against a hard-wired **four-character**
window, so any marker shorter than four could never match — which is why the
shipped engine file uses markers like `<p><` and `.</d`. Since the whole page is
in memory here, matching is "does this marker start at this position", which
removes the constraint instead of working around it.

## Deliberately not built

- No UI. The original's `JSApplet` was ~900 lines doing layout, HTTP, threading,
  state, i18n and browser launching at once. Untangling that is the largest piece
  of work and the least interesting to read about.
- No network `PageFetcher`. Swapping the canned one for `URL::openStream` is a few
  lines; keeping it out means `Demo` runs offline and the scraper stays testable.
- No tests, though `BlockScraper` and `EngineRepository` are shaped to be tested
  with string fixtures — which is the point of extracting them.

## Honest scope

The original cannot run: applets are gone from browsers and from the JDK, and
every engine it scraped is dead or blocking. This is worth reading as a design,
not as something to deploy.
