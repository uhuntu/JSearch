# Roadmap

This repository is an **archive** with a small teaching sketch in `modern/`, a
runner in `runner/` that proves the untouched original still works, and one
living descendant in `jsearxng/`. The archive itself is not a search product,
and there is no plan to turn `modern/` into one — no DuckDuckGo, Bing, caching,
rate limiting or deployable aggregator there. That work already exists, in
`jsearxng/`, and it is maintained as the live web changes.

## In scope

- Keep original trees (`Sources/`, `versions/`, `Releases/`, `ENGINES/`, GB8567 docs) frozen and GBK-safe
- Keep provenance honest (what was moved, what was patched, where the original bytes live)
- Keep `modern/` a compiling contrast to the 2002 design: tests, archive fixtures, the URL-key collision demo
- Corrections to analysis, encoding protection, and CI that runs those tests
- The encoding guard (`EncodingGuard`, `verify-gbk.ps1`, the `encoding` CI job),
  which now checks all original files rather than listing ones it assumes are fine
- The `runner` CI job (`runner/verify.sh`), which is the only automated check
  that the original applet still runs, scrapes and dedups; the other two jobs
  never load `Sources/JSApplet.class`

## Out of scope

- Reviving the applet
- Porting the UI
- Building a metasearch service **out of `modern/`** — done, in `jsearxng/`
- “Production adaptation” of `modern/` — same: `jsearxng/` is that, and
  `modern/` keeps its value by stopping where it does
- Community-growth kits that do not help someone read the 2002 source

The first two are not refusals so much as a division of labour: a tool meant to
be used has to live with anti-bot measures, upstream drift and timeouts, and
none of that is archive work. That tool is in this repository —
[`jsearxng/`](jsearxng/), whose 14 commits were merged in from the standalone
[`uhuntu/JSearXNG`](https://github.com/uhuntu/JSearXNG) repository.

## Maybe, if they serve the archive

- More historical context on early-2000s Chinese software practice and GB8567-88
- Extra version snapshots, with provenance
- A visual diff of the four generations

See [CONTRIBUTING.md](CONTRIBUTING.md).
