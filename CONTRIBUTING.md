# Contributing to JSearch Archive

This is a **software archive**, not an active project. The original code cannot
run (applets are gone from browsers and JDK), and every engine it scraped is
dead or blocking.

## What contributions are welcome

### Documentation improvements
- Clarifications in README or analysis docs
- Additional historical context
- Corrections to the technical analysis

### Reference design (`modern/`)
- Bug fixes in the reference implementation
- Additional test cases
- Performance improvements
- New scraper implementations for modern search APIs

### Preservation
- Additional version snapshots (with provenance)
- Encoding verification
- Build system improvements

## What is NOT accepted

### Revival attempts
- Porting to modern UI frameworks
- Re-implementing the applet
- Trying to make the original code run

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

## Code style (modern/ only)

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
