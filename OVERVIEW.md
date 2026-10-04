# Project Overview

## What is JSearch?

JSearch is a **software archive** preserving a Java applet from 1999-2002 that
aggregated search results from multiple engines (Google, Baidu, etc.) into a
single de-duplicated list.

**Status:** Archive (original code cannot run)  
**License:** GPL-2.0  
**Original author:** Hunt Lin <huntlin@public.xm.fj.cn>  
**Archive recovery:** 2026-09-28

---

## Repository Structure

```
JSearch/
├── Sources/              # Original 2002 source (frozen, GBK-encoded)
├── versions/             # Historical snapshots (2000, 2001, 2002)
├── Releases/             # Shipped release files
├── ENGINES/              # Captured HTML pages from 2001
├── docs/                 # GB8567-88 docs + ANALYSIS.md, ARCHITECTURE.md
├── modern/               # Reference design (safe to edit)
│   ├── src/main/java/    # 14 Java files
│   ├── src/test/java/    # 35 tests
│   ├── build.ps1         # PowerShell build
│   ├── build.sh          # Bash build
│   └── Makefile          # Make build
├── scripts/              # Utility scripts
├── .github/              # CI/CD and templates
├── .vscode/              # IDE configuration
├── README.md             # Main documentation
├── OVERVIEW.md           # This document
├── INDEX.md              # Documentation index
├── GLOSSARY.md           # Technical terms
├── TUTORIALS.md          # Software archaeology tutorials
├── ROADMAP.md            # Future plans
├── PROVENANCE.md         # Recovery record
├── CHANGELOG.md          # Version history
├── CODE_OF_CONDUCT.md    # Community standards
├── CONTRIBUTING.md       # Contribution guidelines
├── FAQ.md                # Frequently asked questions
├── LICENSE               # GPL-2.0
├── MIGRATION.md          # Production adaptation guide
├── QUICKSTART.md         # Quick start guide
├── SECURITY.md           # Security policy
└── SUPPORT.md            # Where to get help
```

---

## Key Features

### Archive Preservation
- **4 historical snapshots** (2000-08, 2001-12, 2002-01, 2002-03)
- **GBK encoding protection** via `.gitattributes`
- **Byte-identical verification** of original files
- **Complete documentation** (GB8567-88 standard, 8 documents)

### Reference Design (`modern/`)
- **Component separation** — 13 files, single responsibility
- **Modern concurrency** — `ExecutorService`, `Future.cancel(true)`
- **Thread-safe dedup** — Single-lock check-then-act
- **Testable** — 35 tests, archive fixtures
- **No dependencies** — Runs on bare JDK 11+

### Infrastructure
- **4 build systems** — PowerShell, Bash, Make, Docker
- **CI/CD** — GitHub Actions (35 tests on every push/PR)
- **IDE support** — VSCode, EditorConfig
- **Community** — Issue/PR templates, Code of Conduct

---

## Documentation Map

| Document | Purpose | Audience |
|----------|---------|----------|
| [README.md](README.md) | Project overview | Everyone |
| [QUICKSTART.md](QUICKSTART.md) | Get started in 5 minutes | New contributors |
| [FAQ.md](FAQ.md) | Common questions | Everyone |
| [ANALYSIS.md](docs/ANALYSIS.md) | Design defects | Developers, historians |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Visual comparison | Developers |
| [MIGRATION.md](MIGRATION.md) | Production adaptation | Developers adapting the design |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to contribute | Contributors |
| [CHANGELOG.md](CHANGELOG.md) | Version history | Everyone |
| [SECURITY.md](SECURITY.md) | Security policy | Security researchers |
| [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | Community standards | Everyone |

---

## Build Options

| Method | Platform | Command |
|--------|----------|---------|
| PowerShell | Windows | `.\build.ps1 -Test` |
| Bash | Linux/Mac | `./build.sh -t` |
| Make | Any | `make test` |
| Docker | Any | `docker run --rm jsearch` |

---

## Test Coverage

**35 tests** covering:
- Engine parsing (5 tests)
- Engine identity and URL building (3 tests)
- HTML scraping (5 tests)
- Result collection and dedup (3 tests)
- Search service concurrency (3 tests)
- JSON API scraping (7 tests)
- Archive fixtures (9 tests)

Run: `cd modern && ./build.ps1 -Test`

---

## Historical Context

### Timeline
- **1999** — Project starts
- **2000-08** — v1.2.3 baseline (1,512 lines)
- **2001-12** — Last large version (1,529 lines)
- **2002-01** — Rewrite to v2.0.0.0 (906 lines)
- **2002-03** — Deadlock patch (still broken)
- **2015-21** — Applets removed from browsers
- **2026-09** — Archive recovered
- **2026-10** — Infrastructure added

### Why it matters
- **Software archaeology** — Preserves a piece of early web history
- **Design lessons** — Shows common pitfalls (God class, manual threading, encoding issues)
- **Reference design** — Demonstrates how to do it right
- **Cultural artifact** — Documents Chinese software development in the early 2000s

---

## Quick Links

- [GitHub Repository](https://github.com/uhuntu/JSearch)
- [Issues](https://github.com/uhuntu/JSearch/issues)
- [Pull Requests](https://github.com/uhuntu/JSearch/pulls)
- [Actions](https://github.com/uhuntu/JSearch/actions)

---

## Contact

- **Original author:** Hunt Lin <huntlin@public.xm.fj.cn> (1999-2002)
- **Archive maintainer:** See GitHub profile

---

## License

GNU General Public License version 2.0 (GPL-2.0)  
See [LICENSE](LICENSE) for details.
