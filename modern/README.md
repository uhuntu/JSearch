# JSearch — reference design

A small, compiling sketch of how JSearch should have been put together. **This is
not a revival, not a port, and not a search product.** The original source, in
all four generations, is untouched one directory up. This directory answers the
question the archive raises: *how should it have been built?*

Default tests use canned HTML (archive fixtures), so the pipeline runs on any
JVM with no live engines. `HttpPageFetcher` and `WebServer` exist as illustrations
of seams the 2002 applet lacked — they are not a service to host.

## Build and run

Requires JDK 11 or later. UTF-8 throughout — the original's GBK is not carried
over.

**PowerShell (Windows):**

```powershell
.\build.ps1          # compile
.\build.ps1 -Test    # compile and run tests
.\build.ps1 -Web     # local demo of the 2002 URL-key bug (http://localhost:8080)
.\build.ps1 -Clean   # clean build output
```

**Bash (Linux/Mac):**

```bash
./build.sh           # compile
./build.sh -t        # compile and run tests
./build.sh -w        # local demo of the 2002 URL-key bug
./build.sh -c -t     # clean, compile, and run tests
```

**Make (any platform with make):**

```bash
make                 # compile
make test            # compile and run tests
make web             # local demo of the 2002 URL-key bug
make clean           # clean build output
```

**Maven (any platform with mvn):**

```bash
mvn test             # compile and run the test suite; any failure fails the build
mvn package          # the same, plus a runnable jar (jsearch.Demo is the main class)
```

**Docker:**

```bash
docker build -t jsearch ..
docker run --rm -p 8080:8080 jsearch
```

**Manual build:**

```sh
javac -encoding UTF-8 -d out $(find src -name '*.java')
java  -Dfile.encoding=UTF-8 -cp out jsearch.Demo
# local archaeology demo (fixtures + URL-key comparison):
java  -Dfile.encoding=UTF-8 -cp out jsearch.WebServer --port 8080
```

## Local demo (`WebServer`)

`jsearch.WebServer` is a JDK `HttpServer` page that runs the sketch against
2001 archive fixtures and shows, side by side, how the 2002 URL-as-key
`Hashtable` dropped Chinese Google. HTTP routes (`/api/compare`, `/api/search`,
…) exist so that demo and its tests can talk to the same process. They are not
a public API.

## Tests

```powershell
.\build.ps1 -Test    # 44 tests, plain JDK, no JUnit or build tool needed
```

They cover engine parsing (shared URLs, duplicate identity, truncated/empty/CRLF
input), URL building, the scraper (document order, markers shorter than four
characters, markers inside tags, truncated pages terminate), `ResultCollector`
dedup under 8 threads, and `SearchService` (cross-engine dedup, one failing
engine not sinking the rest, `cancel(true)` interrupting a blocked fetch).

Thirteen of them run against **the archive's own files** — the four captured
pages in `../ENGINES/` and the shipped `../Releases/JSEngines.txt` — read as GBK
by `ArchiveFixtures`. They skip, rather than fail, when the archive is not next
to this directory, so `modern/` still stands alone.

The last four of those are `EncodingGuard`, and they are the reason `modern/`
looks at the 2002 tree at all rather than only at its own code. Every file in the
archive is GBK except `ENGINES/lycos_en.html`, which is ISO-8859-1, and these
check both that those encodings still hold and that nothing has disappeared: a
GBK file saved back as UTF-8 still reads as Chinese, so the damage has to be
found by strict decoding rather than by looking at it. Run it on its own with
`java -cp out jsearch.EncodingGuard` for the full per-file list, including any
file that no expectation covers yet.

Writing them found three real bugs:

- `ResultCollector` had an error listener field and `fireError()` but no way to
  register a listener, so every engine failure was silently dropped.
  `onError(Consumer)` now exists.
- `HtmlBlockScraper.readPreview()` stepped over a tag to its `>`, which meant an
  end marker occurring *inside* a tag could never match. Baidu's shipped end
  marker is `ble>`, and every one of its 24 occurrences in the captured page is
  the tail of `</table>` — so the first Baidu block ran to the end of the page
  and the other nine results were never seen. The original's character window
  compared at every position, tags included, so it did match. Fixed.
- `WebServer.fetchPageContent()` returned a captured page for *any* query: it
  chose a fixture from the engine URL and never looked at the query, though its
  own comment said the fixtures were for `"java"`. Searching `applet` returned
  ten 2001 links to sun.com and java.apache.org, and the UI reported them as the
  answer. A captured page now answers only the query it was captured against —
  `google_en.html` and `lycos_en.html` for `java`, `google_cn.html` and
  `baidu_cn.html` for `西二在线` — and everything else falls through to the
  synthetic pages. It also decoded `lycos_en.html` as GBK, though it is the one
  file in the archive that is ISO-8859-1.

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
| `changeLanguage(int)` switch with hardcoded strings | *(not built)* |
| `setBounds(x,y,w,h)` everywhere | *(not built)* |
| `Runtime.exec(browser + " " + url)` | *(not built)* |

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

- No applet UI. `WebServer` only demonstrates engine identity and fixture
  scraping; it is not a replacement for `JSApplet`.
- No live HTML scraping of Google or Baidu. Pointing a fetcher at those URLs
  would only re-create the fragility this archive documents.

## Optional: JSON fetcher seams (not a product)

`HttpPageFetcher` and `JsonApiScraper` exist to prove that `SearchService` does
not care whether a page is HTML or JSON. They are **not** an invitation to turn
this archive into a live aggregator. `ApiDemo` is a local experiment.

```sh
BRAVE_API_KEY=...  java -cp out jsearch.ApiDemo "your query"
SEARXNG_URL=http://localhost:8080  java -cp out jsearch.ApiDemo "your query"
```

Notes:
- A JSON engine's block markers are unused; give it a placeholder such as
  `{`..`}` (the engine-file format needs six non-blank fields).
- A `SearchService` takes one `BlockScraper`, so run one service per provider
  shape (as `ApiDemo` does). Mixing HTML and JSON engines in one service would
  need a scraper chosen per engine, which is not built.
- SearXNG pages are 1-based and the level is 0-based, so use one level.
- `cancel(true)` interrupts an in-flight HTTP request; a test proves it against
  a local server.
- The tests cover the fetcher (against a local `HttpServer`) and the JSON path
  with fixtures. **Not verified against the live Brave or SearXNG services**:
  the response shapes come from their documentation, and I had no key.

## What the real pages showed

Pointing the scraper at the four 2001 captures, with markers taken from the
shipped `JSEngines.txt` rather than invented, turned up four things that no
inline fixture could have:

**Baidu's end marker is invisible to a scraper that skips tags.** `ble>` occurs
24 times in `baidu_cn.html` and all 24 are inside `</table>`. Any implementation
that jumps from `<` to `>` steps straight over it, and the page collapses into
one result. The original compared its window at every character, so it did not
have this problem — the port did, until the fixture caught it.

**Google's start marker matches the pagination table too.** `<p><` matches the
`<p><div class=n>` that opens the pager, so a Google page yields 11 results, not
10: the eleventh is the "上一页" link, `/search?q=java&hl=zh-CN&start=0&sa=N`.
That is what the original did as well — same markers, same algorithm — so it is
asserted as known behaviour rather than fixed.

**One of the four pages has no engine.** `lycos_en.html` was captured on
2001-12-27 alongside the other three, but the shipped engine file defines only
Chinese Google, Baidu and English Google. Nothing in it mentions Lycos, so the
page has no markers and cannot be scraped by anything the release shipped.

**The captured Google page is page 2, not page 1.** Its pager marks page 2 as
current, so the ten results are results 11–20 for "java". The table in
`../docs/ANALYSIS.md` lists exactly these ten; its heading has been corrected to say
so.

## Honest scope

The original cannot run: applets are gone from browsers and from the JDK, and
every engine it scraped is dead or blocking. Read this as a design contrast,
not as something to deploy. A live metasearch tool belongs in a different repo.
