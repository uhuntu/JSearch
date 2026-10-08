# Generations: what actually separates the four snapshots

[ANALYSIS.md](ANALYSIS.md) reads 1.2.3 against 2.0.0.0 in prose. This page is
the measured companion: every difference between the four surviving snapshots,
with a command that prints each number. Paths are relative to the repository
root. The originals are GBK and are never rewritten here — the commands
convert to UTF-8 under `/tmp` and work on those copies.

## The four snapshots

| Snapshot | Version | Date | `JSApplet.java` | `SearchThread.java` |
|---|---|---|---|---|
| `versions/2000-08/` | 1.2.3 | Aug 2000 | 1,512 lines · 54,024 B | — (monolith) |
| `versions/2001-12/JSApplet/` | 1.2.3 | Dec 2001 | 1,529 lines · 55,606 B | — (monolith) |
| `versions/2002-01/Sources/` | 2.0.0.0 | Jan 2002 | 906 lines · 28,220 B | 241 lines · 8,336 B |
| `Sources/` (live) | 2.0.0.0 + fix | Mar 2002 | 906 lines · 28,220 B | 263 lines · 9,050 B |

`Sources/JSApplet.java` is byte-identical to
`versions/2002-01/Sources/JSApplet.java`: between the January snapshot and the
live tree, exactly one `.java` file differs.

```sh
cmp versions/2002-01/Sources/JSApplet.java Sources/JSApplet.java   # no output: identical
```

## Line endings, before any diff

All four sources are GBK (`file(1)` reports "ISO-8859 text"; it does not know
GBK). `versions/2000-08/` is LF-terminated; the other three are CRLF. That is
why a raw byte compare of 2000-08 against 2001-12 reports 49,513 differing
bytes — every byte after the first CR is shifted, not different. Strip the
carriage returns first and the entire content difference is 72 diff lines.

```sh
file versions/2000-08/JSApplet.java versions/2001-12/JSApplet/JSApplet.java
cmp -l versions/2000-08/JSApplet.java versions/2001-12/JSApplet/JSApplet.java | wc -l
# 49513 — the CR shift, not content
```

## 2000-08 → 2001-12: +17 lines, three real changes

The CR-stripped diff is 72 lines: 24 hunks — 7 changes, 17 single-line
insertions. The substance:

1. **`finalized()` → `finalize()`** (line 464 → 480). The 2000-08 override was
   misspelled, so it never overrode anything; `finalized` occurs once in
   2000-08 and zero times in 2001-12.
2. **`getParameter("smcCh")` → `getParameter("smlCh")`** (line 670 → 686). The
   Choice is `smlCh`; the old code asked for a parameter that does not exist
   and always got null.
3. **`setForeground` before `setText`** (lines 1037–1038 → 1053–1054), with the
   new comment `//要在setText前！` ("must be before setText!").

The rest is the copyright line becoming `(C) 1999-2002` (line 3), the
engine-loading block being re-indented (lines 1235–1257), and 17
whitespace-only lines inserted. No other logic change — consistent with the
version string staying `1.2.3` (lines 818/1493 in 2000-08; 834/1510 in
2001-12).

## 2001-12 → 2002-01: the rewrite, located by counts

The 1,529-line monolith became a 906-line UI shell plus a 241-line
`SearchThread.java`. What the greps say:

| `grep -c` on `JSApplet.java` | 2001-12 | 2002-01 |
|---|---|---|
| `finalize` (of which `void finalize` declarations) | 48 (23) | 2 (1) |
| `.stop()` — `Thread.stop` calls | 14 | 0 |
| `ping` — the PingThread/PingWatch subsystem | 47 | 0 |
| `synchronized` | 19 | 0 |

`synchronized` = 0 does not mean unguarded: the locking moved into
`SearchThread.java`, which owns result collection. The version string moves
`1.2.3` → `2.0.0.0` (line 583 in both 2002 snapshots).

## 2002-01 → 2002-03: one file, nine hunks

All of March is in `SearchThread.java` (+22 lines; the CR-stripped diff is 75
lines across 9 hunks). Three ideas:

1. **Two shared static locks** (+15 lines at hunk `26a27,41`), with the
   comments that state the January bug: a `synchronized` instance method locks
   `this`, and every search thread is a different instance, so the threads
   never excluded each other. Now `static final Object resultLock` and
   `static final Object searchCountLock`.
2. **The check-and-decrement made atomic** (`94,96c109,116`):
   `--JSApplet.actualSearchAllowed` and its follow-up (`buttonStatus(2)`,
   `JSApplet._stop = true`) move inside `synchronized (searchCountLock)`.
3. **Five nested locks become one** (`207c229`):
   `synchronized (resultTable) {synchronized (resultIndex) {…` — five deep —
   becomes a single `synchronized (resultLock)`, with the result built into a
   local `ResultsDetails` before the single put.

The remaining hunks re-indent the block that now sits one level deeper.

## Reproducing every number

```sh
cd "$(git rev-parse --show-toplevel)" && mkdir -p /tmp/gen
iconv -f GBK -t UTF-8 versions/2000-08/JSApplet.java              > /tmp/gen/g0.java
iconv -f GBK -t UTF-8 versions/2001-12/JSApplet/JSApplet.java     > /tmp/gen/g1.java
iconv -f GBK -t UTF-8 versions/2002-01/Sources/JSApplet.java      > /tmp/gen/g2.java
iconv -f GBK -t UTF-8 Sources/JSApplet.java                       > /tmp/gen/g3.java
iconv -f GBK -t UTF-8 versions/2002-01/Sources/SearchThread.java  > /tmp/gen/st2.java
iconv -f GBK -t UTF-8 Sources/SearchThread.java                   > /tmp/gen/st3.java

wc -l /tmp/gen/*.java                                                # 1512/1529/906/906/241/263
cmp versions/2002-01/Sources/JSApplet.java Sources/JSApplet.java     # identical
diff --strip-trailing-cr /tmp/gen/g0.java /tmp/gen/g1.java | wc -l   # 72
diff --strip-trailing-cr /tmp/gen/st2.java /tmp/gen/st3.java | wc -l # 75, 9 hunks
grep -c 'finalized' /tmp/gen/g0.java /tmp/gen/g1.java                # 1 / 0
```

## Related

- [ANALYSIS.md](ANALYSIS.md) — the prose analysis these numbers support
- [PROVENANCE.md](../PROVENANCE.md) — where each snapshot came from
- [modern/README.md](../modern/README.md) — the reference design
