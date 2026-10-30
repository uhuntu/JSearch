# JSearXNG

A real metasearcher, **in Java** — the J is earned, not inherited. The name is
the architecture: **JSearch**, the 2002 Java applet archived in
[`../Sources/`](../Sources/), supplies the skeleton — fan-out, scrape,
deduplicate, show every engine's failure honestly — and **SearXNG**, the local
instance, supplies a community-maintained engine pool behind one JSON call.
Same idea the 2002 author had, in a world that fights back, and the 2002 applet
UI is reborn as the face.

It is the fourth piece of this repository's lineage, and the part maintained as
a live search tool: [`../Sources/`](../Sources/) and
[`../versions/`](../versions/) hold the artifact,
[`../modern/`](../modern/) is the design contrast,
[`../runner/`](../runner/) proves the original still runs, and this is what the
design became when it was pointed at engines that fight back. Its 14 commits
were merged in from the standalone
[`uhuntu/JSearXNG`](https://github.com/uhuntu/JSearXNG) repository, which now
serves only as a pointer back here.

The app is one file (`JSearXNG.java`), JDK standard library only —
`java.net.http`, `com.sun.net.httpserver`, `java.util.concurrent` — no build
step: the source is executed directly. The JSON reader inside is copied from the
teaching sketch ([`../modern/`](../modern/)), which is the lineage helping its
own descendant.

**JDK 16 or later.** The floor is 16, not the 11 the rest of this repository
targets: the code uses records, `instanceof` patterns and text blocks (the HTML
template is one). `test.sh` passes `--release 16`, so an older JDK fails with
"release version not supported" rather than with parse errors inside the
template — which is exactly what happened the first time this suite ran on CI's
JDK 11, and the reason the floor is written down instead of assumed.

```sh
java JSearXNG.java         # http://127.0.0.1:8092/
```

## Service

A systemd user unit keeps it running and starts it at boot:

```sh
systemctl --user status jsearxng      # running on 127.0.0.1:8092
journalctl --user -u jsearxng -f      # logs
loginctl show-user $USER -p Linger     # Linger=yes = starts at boot
```

After editing `JSearXNG.java`, ship it with `systemctl --user restart
jsearxng`. Never start a second `java JSearXNG.java` by hand: the unit's
next start dies on the occupied port and flaps in `activating
(auto-restart)`, while the hand-started process gets none of what the unit
provides — no boot start, no journal, no restart on failure.

## On Windows

The app itself needs nothing Windows-specific — except a JDK whose floor is
16, which the box this archive is developed on did not have (system JDK 11).
A portable Temurin unzipped under `%USERPROFILE%\.jdks\` — no installer, no
PATH change — runs it and passes the offline suite unchanged (153 checks):

```powershell
%USERPROFILE%\.jdks\jdk-21.0.13+9\bin\java.exe JSearXNG.java --port 8092
```

The local SearXNG half lives in WSL2 (Ubuntu 24.04, systemd on): the official
`utils/searxng.sh install all` into `/usr/local/searxng`, then three settings
the default install gets wrong for this use —

- `search.formats` gains `json`: the default instance answers `format=json`,
  the one call JSearXNG makes, with 403;
- `limiter: false`: a private loopback instance has nobody to throttle, and
  the limiter wants valkey, which the installer does not provision;
- the uwsgi app ini gains `http-socket = 127.0.0.1:8888`: the generated ini
  speaks only a unix socket for nginx, so without it nothing listens on TCP
  and every call is a refused connection.

(The script's `safe_search: 2` also went to 0 — the strict setting silently
guts results.)

WSL2 forwards 127.0.0.1:8888 to Windows, but the reverse is not true:
JSearXNG's calls cannot wake a stopped distro, and WSL stops an idle one. A
logon keep-alive — `wsl -d Ubuntu -e sleep infinity` in the Startup folder —
holds it open; without it the searxng engine reports `ConnectException` after
idle until something touches WSL. Verified end to end from this box: an
English query answers from all five reachable engines including searxng's 8,
deduplicated across the layers.

## Engines

| Engine | How | Notes |
|---|---|---|
| Bing | HTML scrape (`li.b_algo`) | works from a plain server fetch today |
| SearXNG | the local instance's JSON API (`127.0.0.1:8888`) | dozens of engines behind one call; result badges name the upstream |
| Marginalia | official public API (`api.marginalia.nu`) | independent web index, no key |
| Wikipedia | official public API (`/w/api.php`) | no key, designed for this |
| Stack Overflow | official public API (`api.stackexchange.com`) | no key, 300 req/day/IP |
| Hacker News | official Algolia public API | no key, designed for this |

The SearXNG entry makes this a metasearcher over a metasearcher: one of the
fanned-out engines is itself a fan-out. Duplicates between the layers are
dropped by the same URL-dedup, and the `duplicatesDropped` counter shows it
working.

Deliberately **not** wired in, because they return bot-challenge pages (or
redirect walls) to a plain fetch from a server IP as of 2026-10: DuckDuckGo
(html and lite endpoints both 202), Google (302), Mojeek (JS check page).
This was verified with real requests before writing the parsers, and is the
same wall the archive's `README.md` predicted for its 2001-era engines.

**Bing also poisons**: after a few requests it stops erroring and starts
serving random popular pages instead of the asked-for results. `runSearch`
runs a relevance sanity-check on every engine's response — if no result
mentions any query term, the engine's slot shows
`response failed the relevance check … likely a bot-block page` instead of
garbage results. The check recognizes Unicode words and overlapping pairs of
Chinese characters, including mixed Chinese/English queries. A one-character
Chinese query or short English-only query has too little evidence for rejection.
When the query is Chinese and the response contains no Chinese at all, the
slot's message depends on the engine: one that cannot answer Chinese anyway
(stackexchange, hn, marginalia) reads *the engine likely does not cover this
language* — a Chinese query against an English-only API is a mismatch by
construction, not a bot wall — while a Chinese-capable engine returning no
Chinese keeps the bot-block suspicion, because that shape is what its
poisoning looks like. This is a keyword heuristic, not a semantic relevance score;
synonyms can still produce false rejections. Do not trust a scraped engine that
returns "success".

## Honesty rules

- One engine failing never sinks the others (the 2002 rule). Each engine's
  outcome — count or error, and its latency — is shown as a chip on the page
  and in the JSON.
- Results are deduplicated by normalized URL; the page reports how many were
  dropped.
- No cache, no tracking, no ads. One request per engine per query — nothing
  auto-retries, nothing hammers.

## Search limits

All queries share eight engine workers and a bounded queue of 64 worker tasks.
The `speed` control limits parallel engines within one query; it does not create
another pool. HTTP request handling also uses eight shared threads.

A search has an eight-second overall budget, including time queued for engine
workers. Individual HTTP requests retain their six-second timeout. When the
budget expires, completed results are returned and unfinished engines report
`search deadline exceeded` in the existing UI and JSON. Active calls receive an
interrupt and queued work is cancelled. If capacity is exhausted, unfinished
engines report `search capacity exhausted`; caller interruption reports
`search cancelled`. Cancelled queued tasks are removed before subsequent queries.
The budget covers engine scheduling and collection, not time waiting for an HTTP
handler or response delivery. Cancellation relies on engine calls honoring
interruption, as the JDK HTTP client does; workers remain globally bounded even
if an added engine ignores it.

## API

```sh
curl 'http://127.0.0.1:8092/api/search?q=searxng'
```

Returns `{query, results[{title,url,snippet,engine}], engines[{engine,count,
error,ms}], durationMs, duplicatesDropped}`.

## Tests

```sh
./test.sh     # 153 checks, no network, exit 0 or 1
```

The suite covers entity handling, query parsing, URL dedup keys, relevance,
Bing redirect unwrapping, escaping, JSON parsing and page rendering. It also
injects fake engines into `SearchRunner` to verify partial failures, overall
deadlines, preservation of completed results, cancellation, per-query speed,
shared concurrency limits and queue saturation. Scheduling checks use latches
and short time budgets; no public network or local SearXNG is required.
The real engines' current availability is not tested by this suite.

Several sections exist because a bug of exactly that shape already shipped and
was found by eye: the repeated-form-key collapse (six `engines=` values
becoming one), the `&amp;` that survived inside Bing tracking URLs, the raw
`&#0183;` in a preview, and the template slot a query containing `${message}`
could expand. The `&#160;` / `&nbsp;` inconsistency and the slot injection were
both caught by this suite before it had a single user.

## Adding an engine

Write `static List<Result> searchX(String query) throws Exception`, add it to
`ENGINES`. If it dies, its exception becomes that engine's chip text — by
construction.

## License

GPL-2.0-or-later, under the repository's root [LICENSE](../LICENSE) — the same
terms as the archive it descends from, and required by them: the embedded JSON
reader comes from [`../modern/`](../modern/) under those terms.
