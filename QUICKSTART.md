# Quick Start Guide

Run the teaching sketch. The 1999–2002 applet itself cannot run.

## Prerequisites

- **JDK 11 or later** — [Download](https://adoptium.net/)
- **Git** — [Download](https://git-scm.com/)
- **PowerShell** (Windows) or **bash** (Linux/Mac)

## Clone the repository

```bash
git clone https://github.com/uhuntu/JSearch.git
cd JSearch
```

## Verify everything works

### Windows (PowerShell)

```powershell
cd modern
.\build.ps1 -Test
```

### Linux/Mac (bash)

```bash
cd modern
chmod +x build.sh
./build.sh -t
```

You should see a pass count and `0 failed`. Archive-fixture tests skip if
`ENGINES/` / `Releases/` are missing.

## Project structure

```
JSearch/
├── Sources/              # Original 2002 source (frozen, do not edit)
├── versions/             # Historical snapshots (2000, 2001, 2002)
├── Releases/             # Shipped release files
├── ENGINES/              # Captured HTML pages from 2001
├── docs/                 # GB8567-88 docs + ANALYSIS.md, ARCHITECTURE.md
├── modern/               # Reference design (safe to edit)
│   ├── src/main/java/    # Reference implementation
│   ├── src/test/java/    # Tests (incl. archive fixtures)
│   └── build.ps1         # Build script
├── README.md             # Main documentation
├── CONTRIBUTING.md       # Contribution guidelines
└── CHANGELOG.md          # Version history
```

## What to read first

1. **[README.md](README.md)** — Project overview and encoding warning
2. **[ANALYSIS.md](docs/ANALYSIS.md)** — What's wrong with the original design
3. **[ARCHITECTURE.md](docs/ARCHITECTURE.md)** — Visual comparison of old vs new
4. **[modern/README.md](modern/README.md)** — How it should have been built

## Common tasks

### Run the demo

```powershell
cd modern
.\build.ps1
java "-Dfile.encoding=UTF-8" -cp out jsearch.Demo
```

### Run tests only

```powershell
.\build.ps1 -Test
```

### Clean build

```powershell
.\build.ps1 -Clean -Test
```

### View original source (with correct encoding)

```bash
iconv -f GBK -t UTF-8 Sources/JSApplet.java | less
```

## Understanding the code

### Original design (2002)

- **906 lines** in a single `JSApplet` class
- God class: UI, HTTP, threading, state, i18n, browser launch
- Hand-rolled threading with `static int actualSearchAllowed`
- 4 duplicate stores for results (Hashtable, Vector, List, Label)
- URL as engine identity key (bug: silently drops engines)

### Modern reference design

- **13 files**, each with a single responsibility
- `ExecutorService` fixed pool instead of manual threads
- `Future.cancel(true)` interrupts blocked socket reads
- Single `ResultCollector` with one lock
- `(name, category)` as engine identity (no collisions)
- Tests covering parsing, concurrent dedup, and archive fixtures

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

**Quick checklist:**
- [ ] Read CONTRIBUTING.md
- [ ] Run tests before and after changes
- [ ] Don't modify `Sources/` or `versions/` (frozen artifacts)
- [ ] Follow commit message convention

## Troubleshooting

### "GBK charset not available"

Some minimal JDK installations don't include GBK. Use a full JDK from Adoptium.

### Tests fail with "archive root not found"

The tests look for `ENGINES/` and `Releases/` directories. Make sure you're running from the `modern/` directory or set `-Djsearch.archive=<path>`.

### Compilation fails

Ensure you're using JDK 11 or later:

```bash
java -version
```

## Next steps

- Read [ANALYSIS.md](docs/ANALYSIS.md) to understand the design defects
- Study [modern/](modern/) to see the reference design
- Check [ARCHITECTURE.md](docs/ARCHITECTURE.md) for visual comparisons
- Open an [issue](https://github.com/uhuntu/JSearch/issues) to discuss ideas
