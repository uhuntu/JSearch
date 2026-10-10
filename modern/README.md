# JSearch — reference design

A small, compiling sketch of how JSearch should have been put together. **This is
not a revival, not a port, and not a search product.** The original source, in
all four generations, is untouched one directory up. This directory answers the
question the archive raises: *how should it have been built?*

Default tests use canned HTML (archive fixtures) and canned JSON, so the suite
runs on any JVM with no network. `HttpPageFetcher` and `WebServer` exist as
illustrations of seams the 2002 applet lacked. There is also a fourth mode that
does leave the machine — `live` — described under
[Optional: the live mode](#optional-the-live-mode) below.

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
`Hashtable` holds an engine's identity one stray byte away from silent loss.
HTTP routes (`/api/compare`, `/api/search`,
…) exist so that demo and its tests can talk to the same process. They are not
a public API.

## Tests

```powershell
.\build.ps1 -Test    # 57 tests, plain JDK, no JUnit or build tool needed
```

They cover engine parsing (shared URLs, duplicate identity, truncated/empty/CRLF
input), URL building, the scraper (document order, markers shorter than four
characters, markers inside tags, truncated pages terminate), `ResultCollector`
dedup under 8 threads, and `SearchService` (cross-engine dedup, one failing
engine not sinking the rest, `cancel(true)` interrupting a blocked fetch). The
live mode's logic — the relevance check, the five JSON shapes, the engine
dispatch, and a whole search run against a canned fetcher — is covered the same
way, so nothing in it needs the network to be tested.

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
shipped `JSEngines.txt` contains two engines whose URL lines differ only by a
stray trailing space on the first — `http://www.google.com/ ` versus
`http://www.google.com/` — so the original's URL-keyed `Hashtable` never
actually collided while the file stayed as shipped: that invisible byte is the
only thing that kept Chinese Google alive (confirmed by running the original
code; see `../runner/`). Remove it — a trim, a re-encode, any editor's silent
"fix" — and the two records collapse to one, dropping Chinese Google without a
word. Here identity does not depend on that luck: both engines load, and a
genuine duplicate is rejected loudly.

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
  scraping, plus one live mode; it is not a replacement for `JSApplet`.
- No live HTML scraping of Google or Baidu. Pointing a fetcher at those URLs
  would only re-create the fragility this archive documents. The live mode's
  engines are the ones that publish a contract instead of a page.

## Optional: the live mode

The JSON fetcher seams above were built to prove `SearchService` does not care
whether a page is HTML or JSON. The `live` mode is what that seam turned out to
be good for: the same `Engine` records, the same `SearchService` fan-out, the
same `ResultCollector` dedup — pointed at four public APIs that need no key.

| Engine | Endpoint | Shape |
|---|---|---|
| Wikipedia | `en.wikipedia.org/w/api.php` | `query.search[]`, url built from the title |
| Stack Exchange | `api.stackexchange.com/2.3/search/advanced` | `items[]`, `link`/`title`/`body` |
| Hacker News | `hn.algolia.com/api/v1/search` | `hits[]`, `title` or `story_title`, url from `objectID` |
| SearXNG | a local instance, `127.0.0.1:8888` by default (`-Djsearch.searxng=`) | `results[]` |

```sh
java -cp out jsearch.WebServer --port 8080
# then: http://localhost:8080/ → the "Live Web" pill, or
curl 'http://localhost:8080/api/search?q=java+applet&mode=live'
```

The response is the same shape the archive modes return, plus one report per
engine — how many results, how long, and why it returned nothing. "One engine
failing is data, not a crash" is only honest if the failure is visible.

Verified against the real APIs for `java applet`: 44 results, 42 after
deduplication, 1.3 seconds, all four engines answering. Every Wikipedia URL the
title template builds resolves (200), which is the check that caught the
encoding bug below.

Three things the live run taught that no fixture could:

**A response that parses can still be about nothing.** Bing, after a few
requests from a server IP, stops failing and starts serving popular pages that
have nothing to do with the query. `RelevanceCheck` drops a response in which no
hit mentions any significant term of the query (tokens longer than two
characters; an empty hit list or a query of only short words passes, because
there is nothing to reject). Bing is not in the set for two reasons at once —
its HTML no longer matches the archive's markers, and it answers server IPs
this way — but the check is here for whatever is added next.

**An unidentified client gets refused.** The JDK's default `User-Agent` is
`Java/17`, and Wikipedia answers 403 to it and 200 to a client that says who it
is. `HttpPageFetcher` now sends `JSearch/1.0 (...)` on every request, and
`withHeader` replaces it per URL prefix rather than joining it — two
`User-Agent` lines on one request is a request an API may refuse.

**Marginalia is out, and it is worth saying why.** Its public JSON API takes the
query as a *path* segment, and the `^` slot encodes a query the way the 2002
applet did — for a query string, where a space becomes `+`. In a path a `+` is a
literal plus, so "java applet" reaches Marginalia as a search for the literal
string "java+applet", which it answers with zero results and no error. Encoding
the slot for paths instead would change what every archive-mode URL looks like,
so the engine stays out and is reached through the local SearXNG instance, which
aggregates it and spells the request correctly. `JsonApiScraper.marginalia()`
keeps its shape and its test for the day that question is answered.

What the live mode is *not*: it is not a product, and it is not a promise. These
four endpoints are public today on the same terms they were verified on; the
archive's own four engines died inside three years. An API contract is the one
thing about a search engine that does not rot, and it is the only reason a live
set is possible here at all.

## Optional: JSON fetcher seams (not a product)

`HttpPageFetcher` and `JsonApiScraper` exist to prove that `SearchService` does
not care whether a page is HTML or JSON. The live mode above is that seam in
use; `ApiDemo` remains the one-provider-per-process experiment it started as.

```sh
BRAVE_API_KEY=...  java -cp out jsearch.ApiDemo "your query"
SEARXNG_URL=http://localhost:8080  java -cp out jsearch.ApiDemo "your query"
```

Notes:
- A JSON engine's block markers are unused; give it a placeholder such as
  `{`..`}` (the engine-file format needs six non-blank fields).
- A `SearchService` takes one `BlockScraper`, so a service that mixes shapes
  needs a scraper chosen per engine. `ApiDemo` runs one service per provider
  shape; the live mode's `LiveEngines.dispatch()` is the other answer — one
  scraper that routes each engine's response to the parser for its shape.
- SearXNG pages are 1-based and the level is 0-based, so use one level. The
  live APIs page by their own limit, so the live mode asks for one level.
- `cancel(true)` interrupts an in-flight HTTP request; a test proves it against
  a local server.
- The tests cover the fetcher (against a local `HttpServer`) and the JSON path
  with fixtures. Brave is still **not verified against the live service** — the
  response shape comes from its documentation, and there was no key. The four
  live engines are verified; see the live mode above.

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
not as something to deploy. The `live` mode is the exception that proves the
rule rather than breaking it — it runs on published contracts, not on scraped
pages, and it is a demonstration that the design survives contact with a real
network, not a service. A metasearch tool meant to be used belongs in a
different repo, and one exists: `../JSearXNG` applies these same lessons
(the collector, the fan-out, the relevance check) to a live aggregator.
