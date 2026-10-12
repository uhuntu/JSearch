# Contributing to JSearch Archive

This is a **software archive** with one living descendant. The original code
cannot run (applets are gone from browsers and JDK), and every engine it
scraped is dead or blocking; `modern/` is a teaching sketch, and `runner/`
proves the untouched artifact still runs. `jsearxng/` is the part that is a
real, working tool — the same design pointed at engines that fight back.

## Scope

**In scope:** keeping the original trees (`Sources/`, `versions/`, `Releases/`,
`ENGINES/`, the GB8567-88 set) frozen and GBK-safe; keeping provenance honest;
`modern/` as a compiling contrast to the 2002 design (tests, archive fixtures,
the URL-key collision demo); corrections to the analysis; and CI that runs the
tests, the encoding guard, and `runner/verify.sh` — the only automated check
that the original applet still runs, scrapes and dedups.

**Out of scope:** reviving the applet; porting the UI; and turning `modern/`
into a product — no new live engines, caching, rate limits, or a deployable
aggregator there. That work exists already, in [`jsearxng/`](jsearxng/), and is
maintained as the live web changes. `modern/` keeps its value by stopping where
it does.

Nothing here is a deployed service. If you find a problem in `modern/` that
would matter to someone running it locally, report it to the maintainer
directly rather than in a public issue.

## What contributions are welcome

### Documentation and analysis
- Clarifications in README or analysis docs
- Additional historical context
- Corrections to the technical analysis

### Reference design (`modern/`)
- Bug fixes in the sketch
- Tests that pin archive behaviour (fixtures, races, engine identity)
- Small clarity fixes that keep the contrast with 2002 readable

### Preservation
- Additional version snapshots (with provenance)
- Encoding verification
- CI that protects GBK files and runs the existing tests

## What is not accepted

### Revival or productization
- Porting to modern UI frameworks
- Re-implementing the applet
- Trying to make the original code run
- New live search engines, caching, rate limits, or a deployable aggregator
  **in `modern/`** — that work belongs in `jsearxng/`, which already does it
- Expanding `WebServer` into a hosted API

### The living descendant (`jsearxng/`)
- New engines, parser fixes, relevance checks
- Tests that pin the entity layer, query parsing, dedup keys and rendering —
  the suite is pure functions, so it runs in CI with no network
- Anything the live web breaks, which is most of what it needs

`jsearxng/` is the one directory whose subject is the present rather than the
past, so its bar is different: it changes when an upstream changes, and its
commits are expected to name what broke. The archive directories do not move
for it.

### Code style changes to original sources
The `Sources/` and `versions/` directories are **frozen artifacts**. Do not
reformat, reorganize, or "fix" the original Java files. The only exception is
the deadlock patch in `Sources/SearchThread.java`, which is documented in
README.md.

## How to contribute

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run the tests: `cd modern && ./build.ps1 -Test` (or `bash build.sh -t`)
5. Submit a pull request

## Code style (`modern/` only)

- UTF-8 encoding throughout
- No external dependencies (tests run on bare JDK)
- Immutable data structures where possible
- Interface-based design for testability
- Comments explain *why*, not *what*

## Commit messages

Follow the existing convention:
- Prefix with component: `modern:`, `README:`, `docs:`, `ANALYSIS:`
- Imperative mood: "add test", "fix race", "clarify section"
- Reference issues if applicable

## Questions?

Open an issue to discuss before making large changes.
