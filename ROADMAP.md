# Roadmap

This repository is an **archive** with a small teaching sketch in `modern/`.
It is not a search product. There is no plan to add DuckDuckGo, Bing, caching,
rate limiting, or a deployable aggregator.

## In scope

- Keep original trees (`Sources/`, `versions/`, `Releases/`, `ENGINES/`, GB8567 docs) frozen and GBK-safe
- Keep provenance honest (what was moved, what was patched, where the original bytes live)
- Keep `modern/` a compiling contrast to the 2002 design: tests, archive fixtures, the URL-key collision demo
- Corrections to analysis, encoding protection, and CI that runs those tests

## Out of scope

- Reviving the applet
- Porting the UI
- Building a metasearch service
- “Production adaptation” of `modern/`
- Community-growth kits that do not help someone read the 2002 source

## Maybe, if they serve the archive

- More historical context on early-2000s Chinese software practice and GB8567-88
- Extra version snapshots, with provenance
- A visual diff of the four generations
- CI that verifies GBK files were not converted

See [CONTRIBUTING.md](CONTRIBUTING.md).
