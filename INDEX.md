# Documentation Index

Start with [README.md](README.md). Most other files exist to support that
archive, not to run a product.

## Read these

| Document | Purpose |
|----------|---------|
| [README.md](README.md) / [README.zh-CN.md](README.zh-CN.md) | What this is, encoding, generations, how to run |
| [PROVENANCE.md](PROVENANCE.md) | Recovery record |
| [docs/ANALYSIS.md](docs/ANALYSIS.md) | Design defects |
| [docs/GENERATIONS.md](docs/GENERATIONS.md) | The four snapshots, measured |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Original vs sketch |
| [docs/COMPARISON.md](docs/COMPARISON.md) | Side-by-side detail |
| [modern/README.md](modern/README.md) | How to run the teaching sketch |
| [runner/README.md](runner/README.md) | How to run the untouched 2002 applet itself |
| [GLOSSARY.md](GLOSSARY.md) | Terms: GB8567-88, GBK, applet, and the rest |
| [CONTRIBUTING.md](CONTRIBUTING.md) | What is accepted, and what is in scope |
| [CHANGELOG.md](CHANGELOG.md) | How this archive has changed |

## Suggested reading order

1. [README.md](README.md) — what this is, and the encoding warning.
2. Run the tests (see *Running it* in the README), then trust the counts.
3. [docs/ANALYSIS.md](docs/ANALYSIS.md) — what is wrong with the original design;
   find the URL-key bug in `Sources/` yourself.
4. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [modern/README.md](modern/README.md)
   — the contrast, then study each component.
5. [runner/README.md](runner/README.md) — the untouched artifact, actually running.
6. [CONTRIBUTING.md](CONTRIBUTING.md) — and what this repository refuses to become.

## The living descendant

[**jsearxng/**](jsearxng/) — the same design, in Java, running against engines
that fight back. This archive keeps the 2002 artifact, the design contrast in
`modern/`, and the proof in `runner/` that the original still runs; `jsearxng/`
is what the design became. It is the only directory here whose subject is the
present rather than the past.
