# Project Overview

JSearch is a **software archive** of a 1999–2002 Java applet that fanned a query
out to several search engines and de-duplicated the scraped results.

It is not a search product. The original cannot run. `modern/` is a compiling
sketch of how that design should have been structured, plus tests against the
2001 HTML captures. It is not a revival.

**Status:** Archive  
**License:** GPL-2.0  
**Original author:** Hunt Lin \<huntlin@public.xm.fj.cn\>  
**Archive recovery:** 2026-09-28

## What to read

| Document | Why |
|----------|-----|
| [README.md](README.md) | Encoding warning, generations, why it cannot be revived |
| [PROVENANCE.md](PROVENANCE.md) | What was moved, what was patched, where original bytes live |
| [docs/ANALYSIS.md](docs/ANALYSIS.md) | The races, the URL-key collision, the rewrite from 1.2.3 to 2.0 |
| [modern/README.md](modern/README.md) | The reference sketch and how to run tests |

Chinese overview: [README.zh-CN.md](README.zh-CN.md).

## Layout (the parts that matter)

```
Sources/      March 2002 source (frozen GBK; SearchThread.java has two documented race fixes)
versions/     2000-08, 2001-12, 2002-01 snapshots
Releases/     shipped cab, HTML, JSEngines.txt
ENGINES/      captured result pages (test fixtures)
docs/         GB8567-88 set + ANALYSIS.md, ARCHITECTURE.md
modern/       reference design + tests (safe to edit)
```

## Run the sketch

```powershell
cd modern
.\build.ps1 -Test
```

That is the only runtime this repo claims. `.\build.ps1 -Web` is a local demo of
the 2002 engine-key bug against archive fixtures, not a service to host.
